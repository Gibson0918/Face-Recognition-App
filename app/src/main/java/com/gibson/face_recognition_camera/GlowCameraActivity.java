package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.madgaze.smartglass.otg.BaseCamera;
import com.madgaze.smartglass.otg.CameraHelper;
import com.madgaze.smartglass.otg.SplitCamera;
import com.madgaze.smartglass.otg.SplitCameraCallback;
import com.madgaze.smartglass.otg.sensor.SplitUSBSerial;
import com.madgaze.smartglass.otg.sensor.USBSerial2;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GlowCameraActivity extends AppCompatActivity {

    private CameraX.LensFacing lens = CameraX.LensFacing.BACK;
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private TextureView textureView;
    private ImageView imageView;
    private FaceNet faceNet;
    private List<FaceRecognition> faceRecognitionList = new ArrayList<>();
    private FirebaseFirestore db;
    private String emailAddr;
    private Animation rotateOpen, rotateClose, fromBottom, toBottom, showHelperText, hideHelperText;
    private FloatingActionButton menuFab, sign_out_fab, searchFab, addFab, editFab;
    private MaterialTextView sign_out_tv, search_tv, add_tv,edit_tv;
    private boolean clicked = false;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private ScreenPresentation screenPresentation;
    private DisplayManager displayManager;
    private Snackbar snackbar;
    private List<String> nameList = new ArrayList<>();
    private Button toggleButton;
    private Bitmap abitmap;
    private Canvas canvas;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;
    private Paint paint, clearPaint, drawPaint, textPaint;
    private Rect bounds, box;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glow_camera);
        //Set activity screen orientation to landscape as we are displaying the activity now on the glasses
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //check if smart glass connection is established
        boolean isConnected = SplitUSBSerial.getInstance(this).isDeviceConnected();

        if(isConnected) {
            //load items and animation if connected and set up the smart glass's camera for usage
            bindDisplayItem();
            loadAnimation();
            SplitCamera.getInstance(this).setPreviewSize(SplitCamera.CameraDimension.DIMENSION_1920_1080);
            SplitCamera.getInstance(this).setFrameFormat(CameraHelper.FRAME_FORMAT_MJPEG);
            SplitCamera.getInstance(this).start(findViewById(R.id.splitCameraView));
            SplitUSBSerial.getInstance(GlowCameraActivity.this).turnOnScreen();
            SplitUSBSerial.getInstance(GlowCameraActivity.this).setBrightness(4);
            //start preview mode to smart glass (untested code), if it is working as intended, user will be able to
            //turn off mobile phone display and video output from mobile phone to smart glass will still function
            initVideo();

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
                            //create new face and add it to the face recognition list
                            FaceRecognition face = new FaceRecognition(Objects.requireNonNull(documentSnapshot.get("Name")).toString(), faceEmbeddings, Objects.requireNonNull(documentSnapshot.get("Relationship")).toString());
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
                Toast.makeText(GlowCameraActivity.this, "Model not loaded successfully", Toast.LENGTH_SHORT).show();
            }

            //check permission for camera
            if (allPermissionsGranted()) {
                textureView.post(this::startCamera); //start camera if permission has been granted by user
            } else {
                ActivityCompat.requestPermissions(GlowCameraActivity.this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }

            searchFab.setOnClickListener(v -> {
                PackageManager pm = GlowCameraActivity.this.getPackageManager();
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
                Snackbar.make(findViewById(R.id.glow_coordinatorLayout), "Retrieving data, please hold on!", Snackbar.LENGTH_LONG).show();
                //redirect to albumActivity
                Intent intent = new Intent(GlowCameraActivity.this, AlbumActivity.class);
                new Thread(() -> {
                    //retrieve names of ppl added
                    db = FirebaseFirestore.getInstance();
                    db.collection(emailAddr).get().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot documentSnapshot : Objects.requireNonNull(task.getResult())) {
                                if (nameList.contains(Objects.requireNonNull(documentSnapshot.get("Name")).toString())) {
                                    continue;
                                } else {
                                    nameList.add(Objects.requireNonNull(documentSnapshot.get("Name")).toString());
                                }
                            }
                            nameList.remove("Show All");
                            nameList.sort(String::compareToIgnoreCase);
                            nameList.add(0, "Show All");

                            //ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(CameraActivity.this, editFab, editFab.getTransitionName());
                            intent.putStringArrayListExtra("nameList", (ArrayList<String>) nameList);
                            startActivity(intent);
                        }
                    });
                }).start();
            });

            sign_out_fab.setOnClickListener(view -> {
                signOut();
            });
        }
        else {
            snackbar = Snackbar.make(findViewById(R.id.glow_coordinatorLayout), "Glow has been disconnected! Switching to device's camera", Snackbar.LENGTH_LONG);
            snackbar.show();
            //SplitUSBSerial.getInstance(GlowCameraActivity.this).turnOffScreen();
            Intent glowIntent = new Intent(GlowCameraActivity.this, CameraActivity.class);
            startActivity(glowIntent);
            finish();
        }

        //Monitor connectivity and start preview when Glow is connected
        SplitCamera.getInstance(this).setCameraCallback(new SplitCameraCallback() {
            @Override
            public void onConnected() {
                //USB Camera (GLOW) is connected.
                SplitCamera.getInstance(GlowCameraActivity.this).startPreview();
                //Start the preview immediately when it is connected.
            }

            @Override
            public void onDisconnected() {
                //USB Camera (GLOW) is disconnected.
            }

            @Override
            public void onError(int code) {
                //Code 1: There is no connecting MAD Gaze Cameras.
            }
        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        SplitUSBSerial.getInstance(this).onStart();
        SplitCamera.getInstance(this).onStart();
    }

    @Override
    protected void onResume(){
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
    protected void onStop() {
        super.onStop();
        SplitUSBSerial.getInstance(this).onStart();
        SplitCamera.getInstance(this).onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SplitCamera.getInstance(this).onPause();
    }

    //remove the loaded face recognition model from memory
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SplitCamera.getInstance(this).onDestroy();
        faceNet.close();
    }

    //untested code here
    private void startCamera() {
        SplitCamera.getInstance(this).setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] yuv420sp) {
                //Retrieve video data in YUV420sp format

                /* Just an example how to convert the data to Bitmap */
                int width = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewWidth();
                int height = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewHeight();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuvImage = new YuvImage(yuv420sp, ImageFormat.NV21, width, height, null);
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
                byte[] imageBytes = out.toByteArray();
                Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                //Handle with your *image* data
                //Pass the image to face detector
                initDetector(image);
                initDrawingUtils(image);
            }
        });
    }

    public synchronized void initDetector(Bitmap orgImage){
        FirebaseVisionFaceDetectorOptions detectorOptions = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .enableTracking()
                .build();
        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions);
        //retrieve a list of faces detected (firebaseVisionFaces) and perform facial recognition
        faceDetector.detectInImage(FirebaseVisionImage.fromBitmap(orgImage)).addOnSuccessListener(firebaseVisionFaces -> {
            if (!firebaseVisionFaces.isEmpty()) {
                //comparing face embeddings here
                new Thread(()->{
                    try {
                        processFaces(firebaseVisionFaces, orgImage);
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
                            Bitmap croppedFaceBitmap = getFaceBitmap(face, orgImage);
                            if(croppedFaceBitmap != null) {
                                LayoutInflater inflater = GlowCameraActivity.this.getLayoutInflater();
                                View dialogLayout = inflater.inflate(R.layout.add_face_dialog, null);
                                ImageView ivFace = dialogLayout.findViewById(R.id.dlg_image);
                                TextView tvTitle = dialogLayout.findViewById(R.id.dlg_title);
                                EditText etName = dialogLayout.findViewById(R.id.dlg_input);
                                EditText relationShipTxt = dialogLayout.findViewById(R.id.dlg_relationship_input);
                                tvTitle.setText("Add Face");
                                ivFace.setImageBitmap(croppedFaceBitmap);
                                etName.setHint("Input Name");
                                relationShipTxt.setHint("Input Relationship");
                                AlertDialog builder = new AlertDialog.Builder(GlowCameraActivity.this).setView(dialogLayout).setPositiveButton("Ok", null).create();
                                builder.setOnShowListener(new DialogInterface.OnShowListener() {
                                    @Override
                                    public void onShow(DialogInterface dialog) {
                                        Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                        button.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                String name = etName.getText().toString();
                                                String relationship = relationShipTxt.getText().toString();
                                                if (name.isEmpty() || relationship.isEmpty() ) {
                                                    Snackbar.make(v,"Please fill in all details!", Snackbar.LENGTH_SHORT).show();
                                                }
                                                else {
                                                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                                    orgImage.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                                                    byte[] byteArray = byteArrayOutputStream .toByteArray();
                                                    String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                                                    faceRecognitionList = faceNet.addFaceToRecognitionList(name, encoded ,croppedFaceBitmap, faceRecognitionList, db, emailAddr, relationship, GlowCameraActivity.this);
                                                    dialog.dismiss();
                                                }
                                            }
                                        });
                                    }
                                });
                                builder.show();
                            }
                            else {
                                Toast.makeText(GlowCameraActivity.this,"Please try again, error capturing face image!", Toast.LENGTH_LONG).show();
                            }
                        }
                        else {
                            Toast.makeText(GlowCameraActivity.this,"Please ensure that only 1 face is shown!", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
            else {
                //clear the canvas/drawings of rect boxes when there are no faces detected
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
            }
        });

    }

    private synchronized Bitmap processFaces(List<FirebaseVisionFace> faces, Bitmap orgImage) throws ExecutionException, InterruptedException {
        int count =0;
        //canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (int i =0; i<faces.size(); i++) {
            Bitmap croppedFaceBitmap = getFaceBitmap(faces.get(i), orgImage);
            if (croppedFaceBitmap == null) {
                return null;
            }
            else {
                if(count <=4){
                    Future<FaceRecognition> faceRecognitionFutureTask = faceNet.recognizeFace(croppedFaceBitmap, faceRecognitionList);
                    FaceRecognition recognizeFace = faceRecognitionFutureTask.get();
                    textPaint.getTextBounds(recognizeFace.getName(),0,recognizeFace.getName().length(),bounds);
                    canvas.drawRect((int) translateX(faces.get(i).getBoundingBox().left),
                            (int) translateY(faces.get(i).getBoundingBox().top - bounds.height()+10),
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

    //setting up the paint and canvas for the drawing of rect boxes
    private void initDrawingUtils(Bitmap orgImage) {
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
            //to set opacity of text background
            //drawPaint.setAlpha(200);
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            textPaint.setTextSize(40);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setStrokeWidth(2f);
            widthScaleFactor = canvas.getWidth() / (orgImage.getWidth() * 1.0f);
            heightScaleFactor = canvas.getHeight() / (orgImage.getHeight() * 1.0f);
            clearPaint = new Paint();
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            bounds = new Rect();
        }).start();
    }

    private synchronized float translateY(float y) {
        return y * heightScaleFactor;
    }

    private synchronized float translateX(float x) {
        return x * widthScaleFactor;
    }

    private Bitmap getFaceBitmap(FirebaseVisionFace face, Bitmap orgImage){

        Bitmap faceBitmap  =  null;
        try {
            faceBitmap = Bitmap.createBitmap(orgImage, face.getBoundingBox().left, face.getBoundingBox().top, face.getBoundingBox().right - face.getBoundingBox().left, face.getBoundingBox().bottom - face.getBoundingBox().top);
        }
        catch (IllegalArgumentException  e){
            // Log.d("Err123",e.getMessage());
        }
        return faceBitmap;
    }


    private void initVideo() {
        if(displayManager == null) {
            displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display[] displays = displayManager.getDisplays();
            if(displays.length > 1) {
                screenPresentation = new ScreenPresentation(GlowCameraActivity.this, displays[1]);
                screenPresentation.show();
            }
        }
    }

    private void bindDisplayItem() {
        textureView = findViewById(R.id.glow_textureView);
        imageView  = findViewById(R.id.glow_imageView);
        faceRecognitionList = new ArrayList<>();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        assert currentUser != null;
        emailAddr = currentUser.getEmail();
        menuFab = findViewById(R.id.glow_menu_fab);
        sign_out_fab = findViewById(R.id.glow_sign_out_fab);
        editFab = findViewById(R.id.glow_edit_fab);
        searchFab =  findViewById(R.id.glow_search_fab);
        addFab = findViewById(R.id.glow_add_fab);
        sign_out_tv = findViewById(R.id.glow_sign_out_tv);
        search_tv = findViewById(R.id.glow_searchTextView);
        add_tv = findViewById(R.id.glow_addTextView);
        edit_tv = findViewById(R.id.glow_editTextView);
        toggleButton = findViewById(R.id.glow_toggleBtn);
    }

    private void loadAnimation(){
        rotateOpen = AnimationUtils.loadAnimation(GlowCameraActivity.this,R.anim.rotate_open_anim);
        rotateClose = AnimationUtils.loadAnimation(GlowCameraActivity.this,R.anim.rotate_close_anim);
        fromBottom = AnimationUtils.loadAnimation(GlowCameraActivity.this,R.anim.from_bottom_anim);
        toBottom = AnimationUtils.loadAnimation(GlowCameraActivity.this,R.anim.to_bottom_anim);
        showHelperText = AnimationUtils.loadAnimation(GlowCameraActivity.this, R.anim.show_hint);
        hideHelperText = AnimationUtils.loadAnimation(GlowCameraActivity.this, R.anim.hide_hint);
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

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        faceNet.close();
        Toast.makeText(GlowCameraActivity.this, "Signed out successfully", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(GlowCameraActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}

