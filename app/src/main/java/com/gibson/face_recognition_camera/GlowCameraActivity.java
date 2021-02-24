package com.gibson.face_recognition_camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.madgaze.smartglass.otg.AudioCallback;
import com.madgaze.smartglass.otg.CameraHelper;
import com.madgaze.smartglass.otg.RecordVideoCallback;
import com.madgaze.smartglass.otg.SplitCamera;
import com.madgaze.smartglass.otg.SplitCameraCallback;
import com.madgaze.smartglass.otg.TakePictureCallback;
import com.madgaze.smartglass.view.SplitCameraView;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.valdesekamdem.library.mdtoast.MDToast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class GlowCameraActivity extends AppCompatActivity {

    SplitCameraView mSplitCameraView;
    String[] RequiredPermissions = new String[]{ Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glow_camera);
        mSplitCameraView = (SplitCameraView) findViewById(R.id.splitCameraView);
        init();
    }

    public void init(){
        setViews();
        setVideo();

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
        (findViewById(R.id.videoBtn)).setOnClickListener(listener);

        (findViewById(R.id.takePictureBtn)).setEnabled(false);
        (findViewById(R.id.videoBtn)).setEnabled(false);
    }

    public void setVideo(){
        SplitCamera.getInstance(this).setFrameFormat(CameraHelper.FRAME_FORMAT_MJPEG);
        SplitCamera.getInstance(this).setPreviewSize(SplitCamera.CameraDimension.DIMENSION_1280_720);
        SplitCamera.getInstance(this).start(findViewById(R.id.splitCameraView));


        /* Insert code segment below if you want to monitor the USB Camera connection status */
        SplitCamera.getInstance(this).setCameraCallback(new SplitCameraCallback() {
            @Override
            public void onConnected() {
                SplitCamera.getInstance(GlowCameraActivity.this).startPreview();
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

        //////

        /* Insert code segment below if you want to retrieve the video frames in nv21 format */
        SplitCamera.getInstance(this).setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] bytes) {

                Log.e("josua","onPreviewResult:"+bytes);
                int width = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewWidth();
                int height = SplitCamera.getInstance(GlowCameraActivity.this).getPreviewHeight();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                byte[] imageBytes = out.toByteArray();
                Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                //Handle with your *image* data


            }
        });

        //////

        /* Insert code segment below if you want to record the video */
        SplitCamera.getInstance(this).setRecordVideoCallback(new RecordVideoCallback() {
            @Override
            public void onVideoSaved(String path) {
                MDToast.makeText(GlowCameraActivity.this, "Video saved success in (" + path + ")", MDToast.LENGTH_SHORT, MDToast.TYPE_SUCCESS).show();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)findViewById(R.id.videoBtn)).setText("START");
                        ((findViewById(R.id.videoBtn))).setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(int code) {
                MDToast.makeText(GlowCameraActivity.this, "Video saved (Error=" + code +")", MDToast.LENGTH_SHORT, MDToast.TYPE_ERROR).show();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)findViewById(R.id.videoBtn)).setText("START");
                        ((findViewById(R.id.videoBtn))).setEnabled(true);
                    }
                });
            }
        });
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
