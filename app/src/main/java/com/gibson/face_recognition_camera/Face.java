package com.gibson.face_recognition_camera;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

import java.util.List;

//This class is simply used to retrieve details from Firestore
public class Face {

    @PropertyName("Embeddings")
    private List<Float> Embeddings;
    @PropertyName("Name")
    private String Name;
    @PropertyName("Relationship")
    private String Relationship;
    @PropertyName("TimeStamp")
    private Timestamp TimeStamp;

    public Face() {}

    public Face(String Name, List<Float> Embeddings, Timestamp TimeStamp, String Relationship) {
        this.Name = Name;
        this.Embeddings = Embeddings;
        this.TimeStamp = TimeStamp;
        this.Relationship = Relationship;
    }


    @PropertyName("Embeddings")
    public List<Float> getEmbeddings() {
        return Embeddings;
    }

    @PropertyName("Name")
    public String getName() {
        return Name;
    }

    @PropertyName("Relationship")
    public String getRelationship() {return Relationship;}

    @PropertyName("TimeStamp")
    public Timestamp getTimeStamp() {
        return TimeStamp;
    }
}
