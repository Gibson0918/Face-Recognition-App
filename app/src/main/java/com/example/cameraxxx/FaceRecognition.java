package com.example.cameraxxx;

import androidx.annotation.Nullable;

public class FaceRecognition {

    private final String name;
    private final float [][] embedding;


    public FaceRecognition(String name, @Nullable float[][] embedding) {
        this.name = name;
        this.embedding = embedding;
    }

    public String getName() {
        return name;
    }

    public float[][] getEmbedding() {
        return embedding;
    }


}
