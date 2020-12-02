package com.gibson.face_recognition_camera;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public interface Base64ImageDao {
    //Insert query
    @Insert(onConflict = REPLACE)
    void insert(Base64Image base64Image);

    //Delete single query
    @Delete
    void delete(Base64Image base64Image);

    //update query
    @Query("UPDATE Base64Image SET Base64_String = :base64 WHERE docID = :docID")
    void update(String docID,String base64);

    @Query("SELECT Base64_String FROM Base64Image WHERE docID =:docID")
    String getImage(String docID);
}
