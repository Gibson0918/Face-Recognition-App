package com.example.cameraxxx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.ActivityChooserView;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.auto.value.AutoValue;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;


import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;


public class FaceTrackingAnalyzer extends MainActivity implements ImageAnalysis.Analyzer {

    private TextureView textureView;
    private  ImageView imageView;
    private Bitmap abitmap;
    private Canvas canvas;
    private Paint paint;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private CameraX.LensFacing lens;
    private FirebaseVisionImage fbImage;
    private Button button;
    private Activity context;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private Paint clearPaint;



    public FaceTrackingAnalyzer(TextureView textureView, ImageView imageView, Button button, CameraX.LensFacing lens, Activity context, FaceNet faceNet, List<FaceRecognition> faceRecognitionList) {
        this.textureView = textureView;
        this.imageView = imageView;
        this.lens = lens;
        this.button = button;
        this.context = context;
        this.faceNet = faceNet;
        this.faceRecognitionList = faceRecognitionList;
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        if (image == null || image.getImage() == null) {
            return;
        }
        int rotation = degreesToFirebaseRotation(rotationDegrees);
        fbImage = FirebaseVisionImage.fromMediaImage(image.getImage(), rotation);
        initDrawingUtils();
        initDetector(image);
        //image.close();

    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    private synchronized void initDetector(ImageProxy imageProxy) {
        FirebaseVisionFaceDetectorOptions detectorOptions = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .enableTracking()
                .build();
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions);
        faceDetector.detectInImage(fbImage).addOnSuccessListener(firebaseVisionFaces -> {
            if (!firebaseVisionFaces.isEmpty()) {
                try {
                    processFaces(firebaseVisionFaces);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(firebaseVisionFaces.size() == 1) {
                    button.setTextColor(Color.GREEN);
                    button.setClickable(true);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            FirebaseVisionFace face = firebaseVisionFaces.get(0);
                            Bitmap croppedFaceBitmap = getFaceBitmap(face);
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            LayoutInflater inflater = context.getLayoutInflater();
                            View dialogLayout = inflater.inflate(R.layout.add_face_dialog, null);
                            ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
                            TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
                            EditText etName = dialogLayout.findViewById(R.id.dlg_input);
                            tvTitle.setText("Add Face");
                            ivFace.setImageBitmap(croppedFaceBitmap);
                            etName.setHint("Input Name");
                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    String name = etName.getText().toString();
                                    if (name.isEmpty()) {
                                        Toast.makeText(getApplicationContext(), "Please enter name", Toast.LENGTH_SHORT).show();
                                    } else {
                                        //Todo: Facial Recognition method to get embeddings and save it to a List
                                        faceRecognitionList = faceNet.addFaceToRecognitionList(name,croppedFaceBitmap,faceRecognitionList);

                                        dialogInterface.dismiss();
                                    }
                                }
                            });
                            builder.setView(dialogLayout);
                            builder.show();
                        }
                    });
                }
                else {
                    button.setTextColor(Color.RED);
                    button.setClickable(false);
                }
            } else {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                button.setTextColor(Color.RED);
                button.setClickable(false);
                imageView.setImageBitmap(abitmap);
            }
        }).addOnFailureListener(e -> Log.i("sad", e.toString())).addOnCompleteListener(new OnCompleteListener<List<FirebaseVisionFace>>() {
            @Override
            public void onComplete(@NonNull Task<List<FirebaseVisionFace>> task) {
                imageProxy.close();

            }
        });
    }

    private void initDrawingUtils() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                abitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
                canvas = new Canvas(abitmap);
                paint = new Paint();
                paint.setColor(Color.BLUE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setTextSize(40);
                widthScaleFactor = canvas.getWidth() / (fbImage.getBitmap().getWidth() * 1.0f);
                heightScaleFactor = canvas.getHeight() / (fbImage.getBitmap().getHeight() * 1.0f);
                clearPaint = new Paint();
                clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }
        }).start();
        /*bitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setTextSize(40);
        widthScaleFactor = canvas.getWidth() / (fbImage.getBitmap().getWidth() * 1.0f);
        heightScaleFactor = canvas.getHeight() / (fbImage.getBitmap().getHeight() * 1.0f);
        paintFace = new Paint();
        paintFace.setColor(Color.GREEN);
        paintFace.setStyle(Paint.Style.STROKE);
        paintFace.setStrokeWidth(2f);
        paint.setTextSize(40);*/
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private synchronized void processFaces(List<FirebaseVisionFace> faces) throws ExecutionException, InterruptedException {


        if(faces.size() <= 3 ) {

            for (FirebaseVisionFace face : faces) {

                Bitmap croppedFaceBitmap = getFaceBitmap(face);
                if (croppedFaceBitmap == null) {
                    return;
                } else {

                        //Todo  have to  optimize the face recognition here too expensive
                        Future<FaceRecognition> faceRecognitionFutureTask = faceNet.recognizeFace(croppedFaceBitmap, faceRecognitionList);
                        FaceRecognition recognizeFace = faceRecognitionFutureTask.get();
                        // FaceRecognition recognizeFace = faceNet.recognizeFace(croppedFaceBitmap,faceRecognitionList);


                        canvas.drawText(recognizeFace.getName(),
                                translateX(face.getBoundingBox().right),
                                translateY(face.getBoundingBox().bottom),
                                paint);

                        Log.d("face",recognizeFace.getName());


                        //FaceRecognition recognizeFace = faceNet.recognizeFace(croppedFaceBitmap,faceRecognitionList);
                        Rect box = new Rect((int) translateX(face.getBoundingBox().left),
                                (int) translateY(face.getBoundingBox().top),
                                (int) translateX(face.getBoundingBox().right),
                                (int) translateY(face.getBoundingBox().bottom));
                        canvas.drawRect(box, paint);


                    }
                }


            }
        else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        }
        imageView.setImageBitmap(abitmap);
    }

    private synchronized float translateY(float y) {
        return y * heightScaleFactor;
    }

    private synchronized float translateX(float x) {
            return x * widthScaleFactor;
    }

    private int degreesToFirebaseRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
            default:
                throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270.");
        }
    }

    private Bitmap getFaceBitmap(FirebaseVisionFace face){

        Bitmap originalFrame = fbImage.getBitmap();
        Bitmap faceBitmap  =  null;
        try {
            faceBitmap = Bitmap.createBitmap(originalFrame, face.getBoundingBox().left, face.getBoundingBox().top, face.getBoundingBox().right - face.getBoundingBox().left, face.getBoundingBox().bottom - face.getBoundingBox().top);
        }
        catch (IllegalArgumentException  e){
            Log.d("Err123",e.getMessage());
        }

        return faceBitmap;
    }

    private static class Recognition {
        private String id;
        private String title;
        private Float distance;
        private Object emb;


        public Recognition(String id, String title, Float distance, Object emb) {
            this.id = id;
            this.title = title;
            this.distance = distance;
            this.emb = emb;
        }


    }





}
