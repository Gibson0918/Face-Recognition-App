package com.gibson.face_recognition_camera;

import com.google.firebase.Timestamp;

import java.util.List;

//This class is simply used to retrieve details from Firestore
public class Face {

    private String Name;
    private List<Float> Embeddings;
    private String Base64;
    private Timestamp TimeStamp;
    private String Relationship;

    public Face(String Name, List<Float> Embeddings, String Base64, Timestamp TimeStamp, String Relationship) {
        this.Name = Name;
        this.Embeddings = Embeddings;
        this.Base64 = Base64;
        this.TimeStamp = TimeStamp;
        this.Relationship = Relationship;
    }

    public Face() {

    }

    public String getName() {
        return Name;
    }

    public List<Float> getEmbeddings() {
        return Embeddings;
    }

    public String getBase64() {
        return Base64;
    }

    public Timestamp getTimeStamp() {
        return TimeStamp;
    }

    public String getRelationship() {return Relationship;}
}
