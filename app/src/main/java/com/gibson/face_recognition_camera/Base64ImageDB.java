package com.gibson.face_recognition_camera;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Base64Image.class},version = 1, exportSchema = false)
public abstract class Base64ImageDB extends RoomDatabase {
    //create database instance
    private static Base64ImageDB database;
    //Define database name
    private static String DATABASE_NAME = "Base64_ImageDB";

    public synchronized static Base64ImageDB getInstance(Context context){
        //Check condition
        if(database==null){
            //Initialize database
            database = Room.databaseBuilder(context.getApplicationContext(),Base64ImageDB.class,DATABASE_NAME)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
        }
        //Return database
        return database;
    }

    public abstract Base64ImageDao base64ImageDao();
}
