package com.gibson.face_recognition_camera;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnSuccessListener;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;


import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

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
    private static final int EMBEDDING_SIZE = 192;
    private Base64ImageDB database;


    private final int [] intValues = new int [IMAGE_HEIGHT * IMAGE_WIDTH];
    private ByteBuffer imgData;

    private MappedByteBuffer tfliteModel;
    private Interpreter tflife;
    private final Interpreter.Options tfliteOptions  = new Interpreter.Options();
    private CompatibilityList compatibilityList = new CompatibilityList();
    private NnApiDelegate nnApiDelegate = null;

    // Initiate face recognition model -> check whether device has a GPU to run inference else it will attempt to use 4 threads for inference.
    public FaceNet(AssetManager assetManager) throws IOException {

        new Thread(()->{
            try {
                tfliteModel = loadModelFile(assetManager);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(compatibilityList.isDelegateSupportedOnThisDevice()){
                GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
                GpuDelegate gpuDelegate = new GpuDelegate((delegateOptions));
                tfliteOptions.addDelegate(gpuDelegate);
            }
            else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                nnApiDelegate = new NnApiDelegate();
                tfliteOptions.addDelegate(nnApiDelegate);
            }
            else {
                tfliteOptions.setNumThreads(4);
            }

            try {
                tflife = new Interpreter(tfliteModel, tfliteOptions);
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
            imgData = ByteBuffer.allocateDirect(BATCH_SIZE
                    * IMAGE_HEIGHT
                    * IMAGE_WIDTH
                    * NUM_CHANNELS
                    * NUM_BYTES_PER_CHANNEL);
            imgData.order(ByteOrder.nativeOrder());
        }).start();
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
    private synchronized float [][] run (Bitmap bitmap)  {
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
        FaceRecognition recognizedFace = new FaceRecognition("unknown",null, "null");
        double smallestDist = Double.MAX_VALUE;
        float[][] unknownfaceEmbeddings = run(bitmap);
        for (FaceRecognition face : faceRecognitionList) {
            double distance = 0;
            float[][] recognizedFaceEmbeddings = face.getEmbedding();
            for (int i = 0; i < EMBEDDING_SIZE; i++) {
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
            recognizedFace = new FaceRecognition("unknown",null,"null");
        }
        //Log.d("face",recognizedFace.getName());
        //Log.d("face",  "dist: "+ smallestDist);
        completableFuture.complete(recognizedFace);
        return completableFuture;
    }


    public List<FaceRecognition> addFaceToRecognitionList(String name, String encodedBase64 , Bitmap bitmap, List<FaceRecognition> faceRecognitionList, FirebaseFirestore db, String emailAddr, String relationship, Activity context){
        float[][] faceEmbeddings = run(bitmap);
        FaceRecognition face = new FaceRecognition(name, faceEmbeddings, relationship);
        faceRecognitionList.add(face);

        Map<String, Object> faces = new HashMap<>();
        faces.put("Name",face.getName());
        List<Float> embeddingsList = new ArrayList<>();

        for(int i = 0; i <192; i ++) {
            embeddingsList.add(faceEmbeddings[0][i]);
        }
        faces.put("Embeddings",embeddingsList);
        faces.put("TimeStamp", FieldValue.serverTimestamp());
        faces.put("Relationship", relationship);
        DocumentReference newData =  db.collection(emailAddr).document();
        newData.set(faces).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                //Snackbar.make(context.findViewById(R.id.coordinatorLayout), "Data added successfully", Snackbar.LENGTH_SHORT).show();
            }
        });
        String docID = newData.getId();
        database = Base64ImageDB.getInstance(context);
        Base64Image data = new Base64Image();
        data.setDocID(docID);
        data.setBase64(encodedBase64);
        database.base64ImageDao().insert(data);
        return faceRecognitionList;
    }

    public void close()  {
        if(tflife != null) {
            tflife.close();
            tflife = null;
            if(null!=nnApiDelegate){
                nnApiDelegate.close();
            }
        }
        tfliteModel = null;
    }

}
