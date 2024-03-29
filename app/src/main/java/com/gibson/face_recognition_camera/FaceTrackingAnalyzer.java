package com.gibson.face_recognition_camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;


import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class FaceTrackingAnalyzer extends CameraActivity implements ImageAnalysis.Analyzer {

    private TextureView textureView;
    private  ImageView imageView;
    private Bitmap abitmap;
    private Canvas canvas;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private FirebaseVisionImage fbImage;
    private FloatingActionButton addFab;
    private Activity context;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private Paint paint, clearPaint, drawPaint, textPaint;
    private Rect bounds, box;
    private FirebaseFirestore db;
    private String emailAddr;

    public FaceTrackingAnalyzer(TextureView textureView, ImageView imageView, FloatingActionButton addFab, Activity context, FaceNet faceNet, List<FaceRecognition> faceRecognitionList, FirebaseFirestore db, String emailAddr) {
        this.textureView = textureView;
        this.imageView = imageView;
        this.addFab = addFab;
        this.context = context;
        this.faceNet = faceNet;
        this.faceRecognitionList = faceRecognitionList;
        this.db = db;
        this.emailAddr = emailAddr;
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
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private synchronized void initDetector(ImageProxy imageProxy) {
        //Initialize the face detector
        FirebaseVisionFaceDetectorOptions detectorOptions = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .enableTracking()
                .build();
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions);
        //retrieve a list of faces detected (firebaseVisionFaces) and perform facial recognition
        faceDetector.detectInImage(fbImage).addOnSuccessListener(firebaseVisionFaces -> {

            //comparing face embeddings here
            new Thread(()->{
                try {
                    processFaces(firebaseVisionFaces);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(abitmap);
                        }
                    });
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            addFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //only allow faces to be added when there is only 1  face in the frame
                    if(firebaseVisionFaces.size() == 1) {
                        FirebaseVisionFace face = firebaseVisionFaces.get(0);
                        Bitmap croppedFaceBitmap = getFaceBitmap(face);
                        Bitmap originalFrame = fbImage.getBitmap();
                        if(croppedFaceBitmap != null) {
                            LayoutInflater inflater = context.getLayoutInflater();
                            View dialogLayout = inflater.inflate(R.layout.add_face_dialog, null);
                            ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
                            TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
                            EditText etName = dialogLayout.findViewById(R.id.dlg_input);
                            TextInputLayout relationshipTIL = dialogLayout.findViewById(R.id.relationshipInputLayout);
                            AutoCompleteTextView relationshipACTV = dialogLayout.findViewById(R.id.relationshipAutoCompleteTextView);
                            ArrayList<String> relationshipArrayList = new ArrayList<String>();
                            relationshipArrayList.add("Family & Relatives");
                            relationshipArrayList.add("Friends");
                            relationshipArrayList.add("Colleagues");
                            relationshipArrayList.add("Others");
                            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(context, R.layout.support_simple_spinner_dropdown_item, relationshipArrayList);
                            relationshipACTV.setAdapter(arrayAdapter);
                            relationshipACTV.setThreshold(1);
                            tvTitle.setText("Add Face");
                            ivFace.setImageBitmap(croppedFaceBitmap);
                            etName.setHint("Input Name");

                            AlertDialog builder = new AlertDialog.Builder(context).setView(dialogLayout).setPositiveButton("Ok", null).create();
                            builder.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(DialogInterface dialog) {
                                    Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                    button.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            String name = etName.getText().toString();

                                            String relationship = relationshipACTV.getText().toString();
                                            if (name.isEmpty() || relationship.isEmpty() ) {
                                                Snackbar.make(v,"Please fill in all details!", Snackbar.LENGTH_SHORT).show();
                                            }
                                            else {
                                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                                originalFrame.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                                                byte[] byteArray = byteArrayOutputStream .toByteArray();
                                                String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                                                faceRecognitionList = faceNet.addFaceToRecognitionList(name, encoded ,croppedFaceBitmap, faceRecognitionList, db, emailAddr, relationship, context);
                                                dialog.dismiss();
                                            }
                                        }
                                    });
                                }
                            });
                            builder.show();
                        }
                        else {
                            Toast.makeText(context,"Please try again, error capturing face image!", Toast.LENGTH_LONG).show();
                        }
                    }
                    else {
                        Toast.makeText(context,"Please ensure that only 1 face is shown!", Toast.LENGTH_LONG).show();
                    }
                }
            });


        }).addOnCompleteListener(new OnCompleteListener<List<FirebaseVisionFace>>() {
            @Override
            public void onComplete(@NonNull Task<List<FirebaseVisionFace>> task) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                imageProxy.close();
            }
        });

    }

    //setting up the paint and canvas for the drawing of rect boxes
    private void initDrawingUtils() {
        new Thread(() -> {
            abitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(abitmap);
            paint = new Paint();
            paint.setColor(Color.BLUE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            drawPaint = new Paint();
            drawPaint.setColor(Color.WHITE);
            drawPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            drawPaint.setStrokeWidth(2f);
            drawPaint.setTextAlign(Paint.Align.CENTER);
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            textPaint.setTextSize(40);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setStrokeWidth(2f);
            widthScaleFactor = canvas.getWidth() / (fbImage.getBitmap().getWidth() * 1.0f);
            heightScaleFactor = canvas.getHeight() / (fbImage.getBitmap().getHeight() * 1.0f);
            clearPaint = new Paint();
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            bounds = new Rect();
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private synchronized Bitmap processFaces(List<FirebaseVisionFace> faces) throws ExecutionException, InterruptedException {
        int count =0;
        for (int i =0; i<faces.size(); i++) {
            Bitmap croppedFaceBitmap = getFaceBitmap(faces.get(i));
            if (croppedFaceBitmap == null) {
                return null;
            }
            else {
                if(count <=4){
                    Future<FaceRecognition> faceRecognitionFutureTask = faceNet.recognizeFace(croppedFaceBitmap, faceRecognitionList);
                    FaceRecognition recognizeFace = faceRecognitionFutureTask.get();
                    textPaint.getTextBounds(recognizeFace.getName(),0,recognizeFace.getName().length(),bounds);
                    canvas.drawRect((int) translateX(faces.get(i).getBoundingBox().left),
                            (int) translateY(faces.get(i).getBoundingBox().top - bounds.height()),
                            (int) translateX(faces.get(i).getBoundingBox().right),
                            (int) translateY(faces.get(i).getBoundingBox().top),drawPaint);

                    canvas.drawText(recognizeFace.getName(),
                            translateX(faces.get(i).getBoundingBox().centerX()),
                            translateY(faces.get(i).getBoundingBox().top-5),
                            textPaint);

                    //draw a rect box around each face
                    box = new Rect((int) translateX(faces.get(i).getBoundingBox().left),
                            (int) translateY(faces.get(i).getBoundingBox().top),
                            (int) translateX(faces.get(i).getBoundingBox().right),
                            (int) translateY(faces.get(i).getBoundingBox().bottom));
                    canvas.drawRect(box, paint);
                    count+=1;
                }
            }
        }
        return abitmap;
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
        }
        return faceBitmap;
    }

}
