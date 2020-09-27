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
import android.graphics.Rect;
import android.media.Image;
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
import androidx.appcompat.widget.ActivityChooserView;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;

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


import java.util.List;


public class FaceTrackingAnalyzer extends MainActivity implements ImageAnalysis.Analyzer {

    private TextureView textureView;
    private  ImageView imageView;
    private Bitmap bitmap;
    private Canvas canvas;
    private Paint paint;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private CameraX.LensFacing lens;
    private FirebaseVisionImage fbImage;
    private Button button;
    private Activity context;


    public FaceTrackingAnalyzer(TextureView textureView, ImageView imageView, Button button, CameraX.LensFacing lens, Activity context) {
        this.textureView = textureView;
        this.imageView = imageView;
        this.lens = lens;
        this.button = button;
        this.context = context;
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        if (image == null || image.getImage() == null) {
            return;
        }
        int rotation = degreesToFirebaseRotation(rotationDegrees);
        fbImage = FirebaseVisionImage.fromMediaImage(image.getImage(), rotation);
        initDrawingUtils();
        initDetector();

    }


    private void initDetector() {
        FirebaseVisionFaceDetectorOptions detectorOptions = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .enableTracking()
                .build();
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions);
        faceDetector.detectInImage(fbImage).addOnSuccessListener(firebaseVisionFaces -> {
            if (!firebaseVisionFaces.isEmpty()) {
                processFaces(firebaseVisionFaces);
                if(firebaseVisionFaces.size() == 1) {
                    button.setTextColor(Color.GREEN);
                    button.setClickable(true);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            FirebaseVisionFace face = firebaseVisionFaces.get(0);
                            Bitmap a = fbImage.getBitmap();
                            Bitmap b = Bitmap.createBitmap(a, face.getBoundingBox().left, face.getBoundingBox().top, face.getBoundingBox().right - face.getBoundingBox().left, face.getBoundingBox().bottom - face.getBoundingBox().top);
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            LayoutInflater inflater = context.getLayoutInflater();
                            View dialogLayout = inflater.inflate(R.layout.add_face_dialog, null);
                            ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
                            TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
                            EditText etName = dialogLayout.findViewById(R.id.dlg_input);
                            tvTitle.setText("Add Face");
                            ivFace.setImageBitmap(b);
                            etName.setHint("Input Name");
                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    String name = etName.getText().toString();
                                    if (name.isEmpty()) {
                                        Toast.makeText(getApplicationContext(), "Please enter name", Toast.LENGTH_SHORT).show();
                                    } else {
                                        //Todo: Facial Recognition method to get embeddings and save it to a Hashmap

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
            }
        }).addOnFailureListener(e -> Log.i("sad", e.toString()));
    }

    private void initDrawingUtils() {
        bitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setTextSize(40);
        widthScaleFactor = canvas.getWidth() / (fbImage.getBitmap().getWidth() * 1.0f);
        heightScaleFactor = canvas.getHeight() / (fbImage.getBitmap().getHeight() * 1.0f);
    }

    private void processFaces(List<FirebaseVisionFace> faces) {
        for (FirebaseVisionFace face : faces) {

            Rect box = new Rect((int) translateX(face.getBoundingBox().left),
                    (int) translateY(face.getBoundingBox().top),
                    (int) translateX(face.getBoundingBox().right),
                    (int) translateY(face.getBoundingBox().bottom));
            canvas.drawText(String.valueOf(face.getTrackingId()),
                    translateX(face.getBoundingBox().right),
                    translateY(face.getBoundingBox().bottom),
                    paint);
            canvas.drawRect(box, paint);
        }
        imageView.setImageBitmap(bitmap);

    }

    private float translateY(float y) {
        return y * heightScaleFactor;
    }

    private float translateX(float x) {
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


}
