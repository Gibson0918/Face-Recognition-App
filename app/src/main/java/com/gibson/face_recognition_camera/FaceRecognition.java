package com.gibson.face_recognition_camera;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class FaceRecognition {

    @PropertyName("Embeddings")
    private  float [][] Embedding;
    @PropertyName("Name")
    private String Name;
    @PropertyName("Relationship")
    private String Relationship;
    @PropertyName("TimeStamp")
    private Timestamp TimeStamp;

    public FaceRecognition() {}


    public FaceRecognition(String Name, @Nullable float[][] Embedding, String Relationship) {
        this.Name = Name;
        this.Embedding = Embedding;
        this.Relationship = Relationship;
    }

    public String getName() {
        return Name;
    }

    public float[][] getEmbedding() {
        return Embedding;
    }

    public String getRelationship() {return  Relationship;}




}
