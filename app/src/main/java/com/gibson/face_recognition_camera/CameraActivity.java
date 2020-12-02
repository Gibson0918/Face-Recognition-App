package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraInfoUnavailableException;

import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;

import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;


import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.madgaze.smartglass.otg.sensor.SplitUSBSerial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.view.View.LAYER_TYPE_HARDWARE;

public class CameraActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private ImageView imageView;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList;
    private FirebaseFirestore db;
    private String emailAddr;
    private Animation rotateOpen, rotateClose, fromBottom, toBottom, showHelperText, hideHelperText;
    private FloatingActionButton menuFab, sign_out_fab, searchFab, addFab, editFab;
    private TextView sign_out_tv, search_tv, add_tv,edit_tv;
    private boolean clicked = false;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private ScreenPresentation screenPresentation;
    private DisplayManager displayManager;
    private Snackbar snackbar;
    private List<String> nameList = new ArrayList<>();
//    private CustomImageView customImageView;

    /*Todo implement a presentation class to mirror phone output to smart glasses*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        boolean isConnected = SplitUSBSerial.getInstance(this).isDeviceConnected();
        if (isConnected) {
            snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout),"Glow is connected! Switching to Glow", Snackbar.LENGTH_LONG);
            snackbar.show();
            initVideo();
        }
        else {
            snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout),"Glow isn't connected! Using Device's camera", Snackbar.LENGTH_LONG);
            snackbar.show();
            bindDisplayItem();
            loadAnimation();


            menuFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setVisibilityFab(clicked);
                    setAnimation(clicked);
                    setClickableFab(clicked);
                    showHelperText(clicked);
                    showHelperTextAnimation(clicked);
                    if (!clicked) {
                        clicked = true;
                        /*if (clicked == true) {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    menuFab.performClick();
                                    clicked = false;
                                }
                            }, 10000);
                        }*/
                    } else {
                        clicked = false;
                    }
                }
            });



        /*Populate the faceRecognitionList on startup from firestore
        running fireStore request on a new thread*/
            new Thread(() -> {
                db = FirebaseFirestore.getInstance();
                db.collection(emailAddr).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                            float[][] faceEmbeddings = new float[1][192];
                            //List<Float> embeddings = (List<Float>) documentSnapshot.get("Embeddings");
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


            // <<Load the facial recognition model  >>
            faceNet = null;
            try {
                faceNet = new FaceNet(getAssets());
            } catch (Exception e) {
                Toast.makeText(CameraActivity.this, "Model not loaded successfully", Toast.LENGTH_SHORT).show();
            }

            //check permission for camera
            if (allPermissionsGranted()) {
                textureView.post(this::startCamera); //start camera if permission has been granted by user
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }

            searchFab.setOnClickListener(v -> {
                PackageManager pm = CameraActivity.this.getPackageManager();
                boolean isInstalled = isPackageInstalled(pm);
                if (isInstalled) {
                    //Toast.makeText(CameraActivity.this, "Google lens already installed", Toast.LENGTH_SHORT).show();
                    Intent launchGL = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
                    if (launchGL != null) {
                        startActivity(launchGL);
                    }
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.lens"));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            editFab.setOnClickListener(v -> {
                Snackbar.make(findViewById(R.id.coordinatorLayout), "Retrieving data, please hold on!", Snackbar.LENGTH_LONG).show();
                Intent intent = new Intent(CameraActivity.this, AlbumActivity.class);
                new Thread(() -> {
                    db = FirebaseFirestore.getInstance();
                    db.collection(emailAddr).get().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                                if(nameList.contains(Objects.requireNonNull(documentSnapshot.get("Name")).toString())){
                                    continue;
                                }
                                else {
                                    nameList.add(Objects.requireNonNull(documentSnapshot.get("Name")).toString());
                                }
                            }
                            nameList.remove("Show All");
                            nameList.sort(String::compareToIgnoreCase);
                            nameList.add(0,"Show All");

                            //ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(CameraActivity.this, editFab, editFab.getTransitionName());
                            intent.putStringArrayListExtra("nameList", (ArrayList<String>) nameList);
                            startActivity(intent);
                        }
                    });
                }).start();
            });

            sign_out_fab.setOnClickListener(view -> signOut());
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        SplitUSBSerial.getInstance(this).onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SplitUSBSerial.getInstance(this).onStart();
    }

    private void initVideo() {
        if(displayManager == null) {
            displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display[] displays = displayManager.getDisplays();
            if(displays.length > 1) {
                screenPresentation = new ScreenPresentation(CameraActivity.this, displays[1]);
                screenPresentation.show();
            }
        }
    }

    private void  bindDisplayItem() {
        textureView = findViewById(R.id.textureView);
        imageView  = findViewById(R.id.imageView);
        faceRecognitionList = new ArrayList<>();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        assert currentUser != null;
        emailAddr = currentUser.getEmail();
        menuFab = findViewById(R.id.menu_fab);
        sign_out_fab = findViewById(R.id.sign_out_fab);
        editFab = findViewById(R.id.edit_fab);
        searchFab =  findViewById(R.id.search_fab);
        addFab = findViewById(R.id.add_fab);
        sign_out_tv = findViewById(R.id.sign_out_tv);
        search_tv = findViewById(R.id.searchTextView);
        add_tv = findViewById(R.id.addTextView);
        edit_tv = findViewById(R.id.editTextView);
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
            edit_tv.startAnimation(showHelperText);
            search_tv.startAnimation(showHelperText);
            add_tv.startAnimation(showHelperText);
        }
        else {
            sign_out_tv.startAnimation(hideHelperText);
            edit_tv.startAnimation(hideHelperText);
            search_tv.startAnimation(hideHelperText);
            add_tv.startAnimation(hideHelperText);
        }
    }

    private void showHelperText(boolean clicked) {
        if(!clicked) {
            sign_out_tv.setVisibility(View.VISIBLE);
            edit_tv.setVisibility(View.VISIBLE);
            search_tv.setVisibility(View.VISIBLE);
            add_tv.setVisibility(View.VISIBLE);
            sign_out_tv.setAlpha(0.7f);
            edit_tv.setAlpha(0.7f);
            search_tv.setAlpha(0.7f);
            add_tv.setAlpha(0.7f);
        }
        else {
            sign_out_tv.setVisibility(View.INVISIBLE);
            edit_tv.setVisibility(View.INVISIBLE);
            search_tv.setVisibility(View.INVISIBLE);
            add_tv.setVisibility(View.INVISIBLE);
        }
    }

    private void  setVisibilityFab (boolean clicked)  {
        if(!clicked){
            sign_out_fab.setVisibility(View.VISIBLE);
            editFab.setVisibility(View.VISIBLE);
            searchFab.setVisibility(View.VISIBLE);
            addFab.setVisibility(View.VISIBLE);
        }
        else {
            sign_out_fab.setVisibility(View.INVISIBLE);
            editFab.setVisibility(View.INVISIBLE);
            searchFab.setVisibility(View.INVISIBLE);
            addFab.setVisibility(View.INVISIBLE);
        }
    }

    private void setAnimation(boolean clicked) {
        if(!clicked) {
            addFab.startAnimation(fromBottom);
            searchFab.startAnimation(fromBottom);
            editFab.startAnimation(fromBottom);
            sign_out_fab.startAnimation(fromBottom);
            menuFab.startAnimation(rotateOpen);
        }
        else {
            addFab.startAnimation(toBottom);
            searchFab.startAnimation(toBottom);
            editFab.startAnimation(toBottom);
            sign_out_fab.startAnimation(toBottom);
            menuFab.startAnimation(rotateClose);
        }
    }

    private void setClickableFab(boolean clicked) {
        if(!clicked){
            addFab.setClickable(true);
            searchFab.setClickable(true);
            editFab.setClickable(true);
            sign_out_fab.setClickable(true);
        }
        else {
            addFab.setClickable(false);
            searchFab.setClickable(false);
            editFab.setClickable(false);
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

        @SuppressLint("RestrictedApi") PreviewConfig.Builder pc = new PreviewConfig
                .Builder()
                .setTargetResolution(new Size(textureView.getWidth(), textureView.getHeight()))
                .setLensFacing(CameraX.LensFacing.BACK);

        Camera2Config.Extender ext = new Camera2Config.Extender(pc);
        //ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(,99));

        Preview preview = new Preview(pc.build());

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
                new FaceTrackingAnalyzer(textureView, imageView, addFab,CameraX.LensFacing.FRONT, CameraActivity.this, faceNet, faceRecognitionList, db, emailAddr));
        CameraX.bindToLifecycle(this, preview, imageAnalysis);
    }

    //not required unless you want to save the bitmap to external storage
//    public String getBatchDirectoryName() {
////        String app_folder_path = "";
////        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
////        File dir = new File(app_folder_path);
////        if (!dir.exists() && !dir.mkdirs()) {
////            //do something here
////        }
////        return app_folder_path;
////    }

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

    //check if an app required is already installed
    private boolean isPackageInstalled(PackageManager packageManager) {
        try {
            packageManager.getPackageInfo("com.google.ar.lens",0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    //remove the loaded face recognition model from memory
    @Override
    protected void onDestroy() {
        super.onDestroy();
        faceNet.close();
    }

    private void signOut() {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(CameraActivity.this, "Signed out successfully", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(CameraActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();

    }
}