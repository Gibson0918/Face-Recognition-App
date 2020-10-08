package com.example.cameraxxx;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.media.FaceDetector;
import android.os.Build;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.proto.TargetGlobal;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FaceNet {
    private static final String MODEL_PATH = "mobile_face_net.tflite";
    private static final int BATCH_SIZE = 1;
    private static final int IMAGE_HEIGHT = 112;
    private static final int IMAGE_WIDTH = 112;
    private static final int NUM_CHANNELS = 3;
    private static final int NUM_BYTES_PER_CHANNEL = 4;
    private static final int EMBEEDINNG_SIZE = 192;

    private final int [] intValues = new int [IMAGE_HEIGHT * IMAGE_WIDTH];
    private ByteBuffer imgData;

    private MappedByteBuffer tfliteModel;
    private Interpreter tflife;
    private final Interpreter.Options tfliteOptions  = new Interpreter.Options();
    CompatibilityList compatibilityList = new CompatibilityList();

    // Initiate face recognition model -> check whether device has a GPU to run inference else it will attempt to use 4 threads for inference.
    public FaceNet (AssetManager assetManager) throws IOException {
        tfliteModel = loadModelFile(assetManager);
        if(compatibilityList.isDelegateSupportedOnThisDevice()){
            GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate((delegateOptions));
            tfliteOptions.addDelegate(gpuDelegate);
        }
        else {
          tfliteOptions.setNumThreads(4);
        }

        tflife = new Interpreter(tfliteModel, tfliteOptions);
        imgData = ByteBuffer.allocateDirect(BATCH_SIZE
                * IMAGE_HEIGHT
                * IMAGE_WIDTH
                * NUM_CHANNELS
                * NUM_BYTES_PER_CHANNEL);
        imgData.order(ByteOrder.nativeOrder());
    }

    //Method to load the model into memory
    private MappedByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffsett = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffsett, declaredLength);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null  ||  bitmap == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0 , bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        //Convert the image to floating point (will look at quantized model in the future)
        int pixel = 0;
        for (int i = 0; i < IMAGE_HEIGHT; ++i) {
            for (int j = 0; j < IMAGE_WIDTH; ++j) {
                final int val = intValues[pixel++];
                addPixelValue(val);
            }
        }
    }

    private void addPixelValue(int pixelValue) {
        try {
            imgData.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
            imgData.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
            imgData.putFloat((pixelValue & 0xFF) / 255.0f);
        }
        catch (BufferOverflowException e){
            //Should never trigger this part (I think)
        }
    }

    private Bitmap resizedBitmap(Bitmap bitmap, int height, int width){
        return  Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    //Method to run inference
    private float [][] run (Bitmap bitmap)  {
        bitmap  = resizedBitmap(bitmap, IMAGE_HEIGHT, IMAGE_WIDTH);
        convertBitmapToByteBuffer(bitmap);
        float [][] faceEmbeddings = new float[1][192];
        tflife.run(imgData, faceEmbeddings);
        return faceEmbeddings;
    }

    //Method to compare embeddings of each face detected to the face embeddings stored in the face recognition list
    @RequiresApi(api = Build.VERSION_CODES.N)
    public Future<FaceRecognition> recognizeFace(Bitmap bitmap, List<FaceRecognition> faceRecognitionList) {
        CompletableFuture<FaceRecognition> completableFuture = new CompletableFuture<>();
        FaceRecognition recognizedFace = new FaceRecognition("unknown",null);
        double smallestDist = Double.MAX_VALUE;
        float[][] unknownfaceEmbeddings = run(bitmap);
        for (FaceRecognition face : faceRecognitionList) {
            double distance = 0;
            float[][] recognizedFaceEmbeddings = face.getEmbedding();
            for (int i = 0; i < EMBEEDINNG_SIZE; i++) {
                float diff = (unknownfaceEmbeddings[0][i] - recognizedFaceEmbeddings[0][i]);
                float result = diff *diff;
                distance += result;
            }
            distance =  Math.sqrt(distance);

            if(smallestDist> distance){
                smallestDist= distance;
                recognizedFace= face;
            }
        }
        if(smallestDist >=1.0f){
            recognizedFace = new FaceRecognition("unknown",null);
        }
        Log.d("face",recognizedFace.getName());
        Log.d("face",  "dist: "+ smallestDist);
        completableFuture.complete(recognizedFace);
        return completableFuture;
    }


    public List<FaceRecognition> addFaceToRecognitionList(String name, Bitmap bitmap, List<FaceRecognition> faceRecognitionList, FirebaseFirestore db, String emailAddr){
        float[][] faceEmbeddings = run(bitmap);
        FaceRecognition face = new FaceRecognition(name, faceEmbeddings);
        faceRecognitionList.add(face);

        Map<String, Object> faces = new HashMap<>();
        faces.put("Name",face.getName());
        List<Float> embeddingsList = new ArrayList<>();

        for(int i = 0; i <192; i ++) {
            embeddingsList.add(faceEmbeddings[0][i]);
        }
        faces.put("Embeddings",embeddingsList);
        db.collection(emailAddr).add(faces).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                Log.d("Success!",documentReference.getId());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("Failed",e.toString());
            }
        });
        return faceRecognitionList;
    }

    public void close()  {
        if(tflife != null) {
            tflife.close();
            tflife = null;
        }
        tfliteModel = null;
    }

}
