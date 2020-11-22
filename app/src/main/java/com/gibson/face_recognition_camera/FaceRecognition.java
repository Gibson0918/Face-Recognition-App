package com.gibson.face_recognition_camera;

import androidx.annotation.Nullable;

public class FaceRecognition {
    

    private final String name;
    private final float [][] embedding;
    private final String relationship;



    public FaceRecognition(String name, @Nullable float[][] embedding, String relationship) {
        this.name = name;
        this.embedding = embedding;
        this.relationship = relationship;
    }

    public String getName() {
        return name;
    }

    public float[][] getEmbedding() {
        return embedding;
    }

    public String getRelationship() {return  relationship;}




}
