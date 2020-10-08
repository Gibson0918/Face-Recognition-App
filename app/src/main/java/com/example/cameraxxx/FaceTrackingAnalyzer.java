package com.example.cameraxxx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;


import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class FaceTrackingAnalyzer extends CameraActivity implements ImageAnalysis.Analyzer {

    private TextureView textureView;
    private  ImageView imageView;
    private Bitmap abitmap;
    private Canvas canvas;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private CameraX.LensFacing lens;
    private FirebaseVisionImage fbImage;
    private FloatingActionButton addFab;
    private Activity context;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private Paint paint, clearPaint, drawPaint, textPaint;
    private Rect bounds, box;
    private FirebaseFirestore db;
    private String emailAddr;


    public FaceTrackingAnalyzer(TextureView textureView, ImageView imageView, FloatingActionButton addFab, CameraX.LensFacing lens, Activity context, FaceNet faceNet, List<FaceRecognition> faceRecognitionList, FirebaseFirestore db, String emailAddr) {
        this.textureView = textureView;
        this.imageView = imageView;
        this.lens = lens;
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
            if (!firebaseVisionFaces.isEmpty()) {
                try {
                    //comparing face embeddings here
                    processFaces(firebaseVisionFaces);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //only allow faces to be added when there is only 1  face in the frame


                    addFab.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(firebaseVisionFaces.size() == 1) {
                                FirebaseVisionFace face = firebaseVisionFaces.get(0);
                                Bitmap croppedFaceBitmap = getFaceBitmap(face);
                                if(croppedFaceBitmap != null) {
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
                                                Toast.makeText(context, "Please enter name", Toast.LENGTH_SHORT).show();
                                            } else {
                                                //Todo: Facial Recognition method to get embeddings and save it to a List
                                                faceRecognitionList = faceNet.addFaceToRecognitionList(name, croppedFaceBitmap, faceRecognitionList, db, emailAddr);
                                                dialogInterface.dismiss();
                                            }
                                        }
                                    });
                                    builder.setView(dialogLayout);
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


            } else {
                //clear the canvas/drawings of rect boxes when there are no faces detected
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                imageView.setImageBitmap(abitmap);
            }
        }).addOnFailureListener(e -> Log.i("sad", e.toString())
        ).addOnCompleteListener(task -> imageProxy.close());
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
            paint.setTextSize(40);
            drawPaint = new Paint();
            drawPaint.setColor(Color.WHITE);
            drawPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            drawPaint.setStrokeWidth(2f);
            drawPaint.setTextAlign(Paint.Align.CENTER);
            //to set opacity of text background
            //drawPaint.setAlpha(200);
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
    private synchronized void processFaces(List<FirebaseVisionFace> faces) throws ExecutionException, InterruptedException {
        //I  have set facial recognition to be performed only when there are less than 3 faces in the frame
        if(faces.size() <= 4 ) {
            for (FirebaseVisionFace face : faces) {
                // get a cropped bitmap of each face
                Bitmap croppedFaceBitmap = getFaceBitmap(face);
                if (croppedFaceBitmap == null) {
                    return;
                } else {
                        //Todo  have to  optimize the face recognition here too expensive
                        Future<FaceRecognition> faceRecognitionFutureTask = faceNet.recognizeFace(croppedFaceBitmap, faceRecognitionList);
                        FaceRecognition recognizeFace = faceRecognitionFutureTask.get();

                        textPaint.getTextBounds(recognizeFace.getName(),0,recognizeFace.getName().length(),bounds);
                        canvas.drawRect((int) translateX(face.getBoundingBox().left),
                                (int) translateY(face.getBoundingBox().top - bounds.height()+10),
                                (int) translateX(face.getBoundingBox().right),
                                (int) translateY(face.getBoundingBox().top),drawPaint);

                        canvas.drawText(recognizeFace.getName(),
                                translateX(face.getBoundingBox().centerX()),
                                translateY(face.getBoundingBox().top-5),
                                textPaint);

                        Log.d("face",recognizeFace.getName());
                        //draw a rect box around each face
                        box = new Rect((int) translateX(face.getBoundingBox().left),
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

}
