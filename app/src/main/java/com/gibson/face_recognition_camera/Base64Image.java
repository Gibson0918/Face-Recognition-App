package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Base64Image {
    @NonNull
    @PrimaryKey
    private String docID;


    @ColumnInfo(name = "Base64_String")
    private String Base64;

    public String getDocID() {
        return docID;
    }

    public void setDocID(String docID) {
        this.docID = docID;
    }

    public String getBase64() {
        return Base64;
    }

    public void setBase64(String base64) {
        Base64 = base64;
    }
}
