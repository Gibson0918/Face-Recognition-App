package com.example.cameraxxx;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.camera.core.CameraInfoUnavailableException;

import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;

import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;


import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private ImageView imageView;
    private Button button;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        textureView = findViewById(R.id.textureView);
        imageView  = findViewById(R.id.imageView);
        button = findViewById(R.id.button);
        faceRecognitionList = new ArrayList<>();
        //Todo Populate the faceRecognitionList on startup from SQLite'
        //<<Thinking whether it is better to use firebase>>

        //running fireStore request on a new thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                db = FirebaseFirestore.getInstance();
                db.collection("Faces").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if(task.isSuccessful()) {
                            for(QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                                float[][] faceEmbeddings = new float[1][192];
                                //List<Float> embeddings = (List<Float>) documentSnapshot.get("Embeddings");
                                List<Double> embeddings = (List<Double>) documentSnapshot.get("Embeddings");

                                for(int i=0; i < 192; i++) {
                                    assert embeddings != null;
                                    faceEmbeddings[0][i] = embeddings.get(i).floatValue();
                                }
                                FaceRecognition face = new FaceRecognition(documentSnapshot.get("Name").toString(), faceEmbeddings);
                                faceRecognitionList.add(face);
                            }
                        }
                    }
                });
            }
        }).start();

        // <<Load the facial recognition model  >>
        faceNet = null;
        try {
            faceNet = new FaceNet(getAssets());
        }
        catch (Exception e){
            Toast.makeText(CameraActivity.this, "Model not loaded successfully", Toast.LENGTH_SHORT).show();
        }

        //check permission for camera
        if(allPermissionsGranted()){
            textureView.post(this::startCamera); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }


    @SuppressLint("RestrictedApi")
    private void startCamera() {

        try {
            CameraX.getCameraWithLensFacing(CameraX.LensFacing.FRONT);
            initCamera();
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
        }
    }

    //method to start up camera and set up camera preview
    private void initCamera() {
        CameraX.unbindAll();
        PreviewConfig pc = new PreviewConfig
                .Builder()
                .setTargetResolution(new Size(textureView.getWidth(), textureView.getHeight()))
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();

        Preview preview = new Preview(pc);

        preview.setOnPreviewOutputUpdateListener(output -> {
            ViewGroup vg = (ViewGroup) textureView.getParent();
            vg.removeView(textureView);
            vg.addView(textureView, 0);
            textureView.setSurfaceTexture(output.getSurfaceTexture());
            
        });

        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig
                .Builder()
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setTargetResolution(new Size(textureView.getWidth(),textureView.getHeight()))
                .setLensFacing(CameraX.LensFacing.BACK).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(Runnable::run,
                new FaceTrackingAnalyzer(textureView, imageView, button,CameraX.LensFacing.FRONT,this, faceNet, faceRecognitionList,db));
        CameraX.bindToLifecycle(this, preview, imageAnalysis);
    }

    //not required unless you want to save the bitmap to external storage
    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {
            //do something here
        }
        return app_folder_path;
    }

    private boolean allPermissionsGranted(){

        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera();
            } else{
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    //remove the loaded face recognition model from memory
    @Override
    protected void onDestroy() {
        super.onDestroy();
        faceNet.close();
    }

}