package com.gibson.face_recognition_camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.protobuf.StringValue;
import com.madgaze.smartglass.otg.AudioCallback;
import com.madgaze.smartglass.otg.BaseCamera;
import com.madgaze.smartglass.otg.CameraHelper;
import com.madgaze.smartglass.otg.RecordVideoCallback;
import com.madgaze.smartglass.otg.SplitCamera;
import com.madgaze.smartglass.otg.SplitCameraCallback;
import com.madgaze.smartglass.otg.TakePictureCallback;
import com.madgaze.smartglass.view.SplitCameraView;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.valdesekamdem.library.mdtoast.MDToast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GlowCameraActivity extends AppCompatActivity {

    SplitCameraView mSplitCameraView;
    String[] RequiredPermissions = new String[]{ Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    FirebaseVisionFaceDetector faceDetector;
    FirebaseVisionImage firebaseVisionImage;
    boolean unset;

    private ImageView rectOverlay;
    private Bitmap abitmap;
    private Canvas canvas;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private FloatingActionButton addFab;
    private Activity context;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private Paint paint, clearPaint, drawPaint, textPaint;
    private Rect bounds, box;
    private FirebaseFirestore db;
    private String emailAddr;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private Button offButton;
    private int width, height;
    private FirebaseVisionImageMetadata firebaseVisionImageMetadata;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glow_camera);
        unset = false;
        rectOverlay = findViewById(R.id.rectOverlay);
        rectOverlay.post(new Runnable() {
            @Override
            public void run() {
                initDrawingUtils();
            }
        });
        faceRecognitionList = new ArrayList<>();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        assert currentUser != null;
        emailAddr = currentUser.getEmail();
        mSplitCameraView = (SplitCameraView) findViewById(R.id.splitCameraView);

        offButton = findViewById(R.id.offGlowButton);
        offButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(faceNet != null){
                    faceNet.close();
                }
                Intent intent = new Intent(GlowCameraActivity.this, CameraActivity.class);
                startActivity(intent);
                finish();
            }
        });

        faceNet = null;
        try {
            faceNet = new FaceNet(getAssets());
        } catch (Exception e) {
            Toast.makeText(GlowCameraActivity.this, "Model not loaded successfully", Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            db = FirebaseFirestore.getInstance();
            db.collection(emailAddr).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                        float[][] faceEmbeddings = new float[1][192];
                        List<Double> embeddings = (List<Double>) documentSnapshot.get("Embeddings");
                        for (int i = 0; i < 192; i++) {
                            assert embeddings != null;
                            faceEmbeddings[0][i] = embeddings.get(i).floatValue();
                        }
                        FaceRecognition face = new FaceRecognition(Objects.requireNonNull(documentSnapshot.get("Name")).toString(), faceEmbeddings,  Objects.requireNonNull(documentSnapshot.get("Relationship")).toString());
                        faceRecognitionList.add(face);
                    }
                }
            });
        }).start();
        init();
    }

    public void init(){
        setViews();
        FirebaseVisionFaceDetectorOptions detectorOptions = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .enableTracking()
                .build();
        faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions);
        setVideo(faceDetector);

        if (!permissionReady()) {
            askForPermission();
        }
    }


    public void askForPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, RequiredPermissions,100);
        }
    }

    public boolean permissionReady(){
        ArrayList<String> PermissionsMissing = new ArrayList();

        StringBuilder sb = new StringBuilder("");

        for (int i = 0; i < RequiredPermissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, RequiredPermissions[i]) != PackageManager.PERMISSION_GRANTED) {
                PermissionsMissing.add(RequiredPermissions[i]);
                sb.append(RequiredPermissions[i]+".");
            }
        }
        if (PermissionsMissing.size() > 0){
            MDToast.makeText(GlowCameraActivity.this, String.format("Permission [%s] not allowed, please allows in the Settings.",sb ), MDToast.LENGTH_SHORT, MDToast.TYPE_ERROR).show();
            return false;
        }
        return true;
    }


    public void setViews(){
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch(view.getId()){
                    case R.id.cameraOnOffBtn:
                        toggleCameraOnOffBtn();
                        break;
                    case R.id.takePictureBtn:
                        findViewById(R.id.takePictureBtn).setEnabled(false);
                        takePicture();
                        break;
                    case R.id.videoBtn:
                        findViewById(R.id.videoBtn).setEnabled(false);
                        toogleVideoBtn();
                        break;
                }
            }
        };

        (findViewById(R.id.cameraOnOffBtn)).setOnClickListener(listener);
        (findViewById(R.id.takePictureBtn)).setOnClickListener(listener);
        (findViewById(R.id.takePictureBtn)).setEnabled(false);
        (findViewById(R.id.videoBtn)).setEnabled(false);
    }


    public synchronized void setVideo(FirebaseVisionFaceDetector faceDetector){
        SplitCamera.getInstance(this).setFrameFormat(CameraHelper.FRAME_FORMAT_MJPEG);
        SplitCamera.getInstance(this).setPreviewSize(SplitCamera.CameraDimension.DIMENSION_1920_1080);
        SplitCamera.getInstance(this).start(findViewById(R.id.splitCameraView));

        /* Insert code segment below if you want to monitor the USB Camera connection status */
        SplitCamera.getInstance(this).setCameraCallback(new SplitCameraCallback() {
            @Override
            public void onConnected() {
                SplitCamera.getInstance(GlowCameraActivity.this).startPreview();
                width = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewWidth();
                height = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewHeight();
                firebaseVisionImageMetadata = new FirebaseVisionImageMetadata.Builder()
                        .setWidth(width)   // 480x360 is typically sufficient for
                        .setHeight(height)  // image recognition
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setRotation(FirebaseVisionImageMetadata.ROTATION_0)
                        .build();
                MDToast.makeText(GlowCameraActivity.this, "Camera connected", MDToast.LENGTH_SHORT, MDToast.TYPE_SUCCESS).show();
                updateUI(true);
            }

            @Override
            public void onDisconnected() {
                MDToast.makeText(GlowCameraActivity.this, "Camera disconnected", MDToast.LENGTH_SHORT, MDToast.TYPE_INFO).show();
                updateUI(false);
            }

            @Override
            public void onError(int code) {
                if (code == -1)
                    MDToast.makeText(GlowCameraActivity.this, "There is no connecting MAD Gaze Cameras.", MDToast.LENGTH_SHORT, MDToast.TYPE_ERROR).show();
                else
                    MDToast.makeText(GlowCameraActivity.this, "MAD Gaze Camera Init Failure (Error=" + code + ")", MDToast.LENGTH_SHORT, MDToast.TYPE_ERROR).show();
                updateUI(false);
            }
        });

        /* Insert code segment below if you want to retrieve the video frames in nv21 format */
        SplitCamera.getInstance(this).setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] bytes) {
                widthScaleFactor = canvas.getWidth() / (width * 1.0f);
                heightScaleFactor = canvas.getHeight() / (height * 1.0f);
                firebaseVisionImage = FirebaseVisionImage.fromByteArray(bytes,firebaseVisionImageMetadata);
                faceDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                        if(!firebaseVisionFaces.isEmpty()) {

                            new Thread(()->{
                                try {
                                    processFaces(firebaseVisionFaces);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            rectOverlay.setImageBitmap(abitmap);
                                        }
                                    });
                                } catch (ExecutionException e) {
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }).start();



                        }
                        else {
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                        }
                    }
                });
                //canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
        });
    }

    public String getBatchDirectoryName() {
        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {
            //do something here
        }
        return app_folder_path;
    }

    private synchronized Bitmap processFaces(List<FirebaseVisionFace> faces) throws ExecutionException, InterruptedException {
        int count =0;
        //canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (int i =0; i<faces.size(); i++) {
            Log.d("Drawing rect process", "Drawed rect process");
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

                    //Log.d("face",recognizeFace.getName());
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

    private void initDrawingUtils() {
        new Thread(() -> {
            abitmap = Bitmap.createBitmap(rectOverlay.getWidth(), rectOverlay.getHeight(), Bitmap.Config.ARGB_8888);
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
            //to set opacity of text background
            //drawPaint.setAlpha(200);
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            textPaint.setTextSize(40);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setStrokeWidth(2f);
            clearPaint = new Paint();
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            bounds = new Rect();
        }).start();
    }

    private Bitmap getFaceBitmap(FirebaseVisionFace face) {

        Bitmap originalFrame = firebaseVisionImage.getBitmap();
        Bitmap faceBitmap = null;
        try {
            faceBitmap = Bitmap.createBitmap(originalFrame, face.getBoundingBox().left, face.getBoundingBox().top, face.getBoundingBox().right - face.getBoundingBox().left, face.getBoundingBox().bottom - face.getBoundingBox().top);
        } catch (IllegalArgumentException e) {
            // Log.d("Err123",e.getMessage());
        }
        return faceBitmap;
    }



    public void toggleCameraOnOffBtn(){
        if (!permissionReady()) return;
        if (SplitCamera.getInstance(GlowCameraActivity.this).isPreviewStarted()) {
            updateUI(false);
            SplitCamera.getInstance(GlowCameraActivity.this).stopPreview();
        } else {
            updateUI(true);
            SplitCamera.getInstance(GlowCameraActivity.this).startPreview();
        }
    }

    public void toogleVideoBtn(){
        if (!permissionReady()) return;
        if (SplitCamera.getInstance(this).isRecording()) {
            MDToast.makeText(this, "Stop Recording", MDToast.LENGTH_SHORT, MDToast.TYPE_INFO).show();
            SplitCamera.getInstance(this).stopRecording();

        } else {
            MDToast.makeText(this, "Start Recording", MDToast.LENGTH_SHORT, MDToast.TYPE_INFO).show();
            SplitCamera.getInstance(this).startRecording();
            ((Button)(findViewById(R.id.videoBtn))).setText("STOP VIDEO");
            ((findViewById(R.id.videoBtn))).setEnabled(true);

        }
    }

    public void takePicture() {
        if (!permissionReady()) return;
        SplitCamera.getInstance(this).takePicture(new TakePictureCallback() {
            @Override
            public void onImageSaved(String path) {
                MDToast.makeText(GlowCameraActivity.this, "Image saved success in (" + path + ")", MDToast.LENGTH_SHORT, MDToast.TYPE_SUCCESS).show();
                ((findViewById(R.id.takePictureBtn))).setEnabled(true);
            }

            @Override
            public void onError(int code) {
                MDToast.makeText(GlowCameraActivity.this, "Image saved failure (Error="+code+")", MDToast.LENGTH_SHORT, MDToast.TYPE_SUCCESS).show();
                ((findViewById(R.id.takePictureBtn))).setEnabled(true);
            }
        });
    }

    public void updateUI(boolean on){
        if (on){
            (findViewById(R.id.cameraOnOffBtn)).setEnabled(true);
            ((Button)findViewById(R.id.cameraOnOffBtn)).setText("STOP");
            (findViewById(R.id.videoBtn)).setEnabled(true);
            (findViewById(R.id.takePictureBtn)).setEnabled(true);

        } else {
            (findViewById(R.id.cameraOnOffBtn)).setEnabled(true);
            ((Button)findViewById(R.id.cameraOnOffBtn)).setText("START");
            (findViewById(R.id.videoBtn)).setEnabled(false);
            (findViewById(R.id.takePictureBtn)).setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SplitCamera.getInstance(this).onDestroy();
        faceNet.close();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SplitCamera.getInstance(this).onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SplitCamera.getInstance(this).onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SplitCamera.getInstance(this).onResume();
        faceRecognitionList.clear();
        new Thread(() -> {
            db = FirebaseFirestore.getInstance();
            db.collection(emailAddr).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                        float[][] faceEmbeddings = new float[1][192];
                        List<Double> embeddings = (List<Double>) documentSnapshot.get("Embeddings");
                        for (int i = 0; i < 192; i++) {
                            assert embeddings != null;
                            faceEmbeddings[0][i] = embeddings.get(i).floatValue();
                        }
                        FaceRecognition face = new FaceRecognition(Objects.requireNonNull(documentSnapshot.get("Name")).toString(), faceEmbeddings,  Objects.requireNonNull(documentSnapshot.get("Relationship")).toString());
                        faceRecognitionList.add(face);
                    }
                }
            });
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SplitCamera.getInstance(this).onPause();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 100: {
                if (!permissionReady()) {
                    askForPermission();
                }
                return;
            }
        }
    }

}
