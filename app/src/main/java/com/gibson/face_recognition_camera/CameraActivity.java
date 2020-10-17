package com.gibson.face_recognition_camera;

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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    private String emailAddr;
    private Animation rotateOpen, rotateClose, fromBottom, toBottom, showHelperText, hideHelperText;
    private FloatingActionButton menuFab, sign_out_fab, editFab, addFab, brightnessFab;
    private TextView sign_out_tv, edit_tv, add_tv, brightness_tv;
    private boolean clicked = false;

    //Todo implement a foreground service for app if required by the smart glasses
    // so that the app will be running in the background while streaming its output to the
    // smart glasses and users will be able to use the phone for other processes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        bindDisplayItem();
        loadAnimation();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        menuFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVisibilityFab(clicked);
                setAnimation(clicked);
                setClickableFab(clicked);
                showHelperText(clicked);
                showHelperTextAnimation(clicked);
                if(!clicked) {
                    clicked = true;
                }
                else {
                    clicked = false;
                }
            }
        });

        //Populate the faceRecognitionList on startup from firestore'
        //running fireStore request on a new thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                db = FirebaseFirestore.getInstance();
                db.collection(emailAddr).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
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

        //Todo: Rethink approach here as interpreter is running on the main thread which may be the cause of lags when too many faces appear within a frame
        // as it is blocking the main thread (UI thread) from drawing the rect boxes around faces or loading the FAB animation smoothly

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

        sign_out_fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signOut();
            }
        });
    }

    private void  bindDisplayItem() {
        textureView = findViewById(R.id.textureView);
        imageView  = findViewById(R.id.imageView);
        faceRecognitionList = new ArrayList<>();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        emailAddr = account.getEmail();
        menuFab = findViewById(R.id.menu_fab);
        sign_out_fab = findViewById(R.id.sign_out_fab);
        brightnessFab = findViewById(R.id.brightness_fab);
        editFab =  findViewById(R.id.edit_fab);
        addFab = findViewById(R.id.add_fab);
        sign_out_tv = findViewById(R.id.sign_out_tv);
        edit_tv = findViewById(R.id.editTextView);
        add_tv = findViewById(R.id.addTextView);
        brightness_tv = findViewById(R.id.brightnessTextView);
    }

    private void loadAnimation(){
        rotateOpen = AnimationUtils.loadAnimation(CameraActivity.this,R.anim.rotate_open_anim);
        rotateClose = AnimationUtils.loadAnimation(CameraActivity.this,R.anim.rotate_close_anim);
        fromBottom = AnimationUtils.loadAnimation(CameraActivity.this,R.anim.from_bottom_anim);
        toBottom = AnimationUtils.loadAnimation(CameraActivity.this,R.anim.to_bottom_anim);
        showHelperText = AnimationUtils.loadAnimation(CameraActivity.this, R.anim.show_hint);
        hideHelperText = AnimationUtils.loadAnimation(CameraActivity.this, R.anim.hide_hint);
    }

    private void showHelperTextAnimation(boolean clicked) {
        if(!clicked) {
            sign_out_tv.startAnimation(showHelperText);
            brightness_tv.startAnimation(showHelperText);
            edit_tv.startAnimation(showHelperText);
            add_tv.startAnimation(showHelperText);
        }
        else {
            sign_out_tv.startAnimation(hideHelperText);
            brightness_tv.startAnimation(hideHelperText);
            edit_tv.startAnimation(hideHelperText);
            add_tv.startAnimation(hideHelperText);
        }
    }

    private void showHelperText(boolean clicked) {
        if(!clicked) {
            sign_out_tv.setVisibility(View.VISIBLE);
            brightness_tv.setVisibility(View.VISIBLE);
            edit_tv.setVisibility(View.VISIBLE);
            add_tv.setVisibility(View.VISIBLE);
            sign_out_tv.setAlpha(0.7f);
            brightness_tv.setAlpha(0.7f);
            edit_tv.setAlpha(0.7f);
            add_tv.setAlpha(0.7f);
        }
        else {
            sign_out_tv.setVisibility(View.INVISIBLE);
            brightness_tv.setVisibility(View.INVISIBLE);
            edit_tv.setVisibility(View.INVISIBLE);
            add_tv.setVisibility(View.INVISIBLE);
        }
    }

    private void  setVisibilityFab (boolean clicked)  {
        if(!clicked){
            sign_out_fab.setVisibility(View.VISIBLE);
            brightnessFab.setVisibility(View.VISIBLE);
            editFab.setVisibility(View.VISIBLE);
            addFab.setVisibility(View.VISIBLE);
        }
        else {
            sign_out_fab.setVisibility(View.INVISIBLE);
            brightnessFab.setVisibility(View.INVISIBLE);
            editFab.setVisibility(View.INVISIBLE);
            addFab.setVisibility(View.INVISIBLE);
        }
    }

    private void setAnimation(boolean clicked) {
        if(!clicked) {
            addFab.startAnimation(fromBottom);
            editFab.startAnimation(fromBottom);
            brightnessFab.startAnimation(fromBottom);
            sign_out_fab.startAnimation(fromBottom);
            menuFab.startAnimation(rotateOpen);
        }
        else {
            addFab.startAnimation(toBottom);
            editFab.startAnimation(toBottom);
            brightnessFab.startAnimation(toBottom);
            sign_out_fab.startAnimation(toBottom);
            menuFab.startAnimation(rotateClose);
        }
    }

    private void setClickableFab(boolean clicked) {
        if(!clicked){
            addFab.setClickable(true);
            editFab.setClickable(true);
            brightnessFab.setClickable(true);
            sign_out_fab.setClickable(true);
        }
        else {
            addFab.setClickable(false);
            editFab.setClickable(false);
            brightnessFab.setClickable(false);
            sign_out_fab.setClickable(false);
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
                new FaceTrackingAnalyzer(textureView, imageView, addFab,CameraX.LensFacing.FRONT,this, faceNet, faceRecognitionList,db,emailAddr));
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

    private void signOut() {
        mGoogleSignInClient.signOut()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(CameraActivity.this, "Signed out successfully", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(CameraActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
    }
}