package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


public class DetailsActivity extends AppCompatActivity {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ImageView imageView;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        imageView = findViewById(R.id.orgImageview);
        textView = findViewById(R.id.nameTextView);
        Intent intent = getIntent();
        String key = intent.getStringExtra("key");
        String emailAddr = intent.getStringExtra("emailAddr");
        DocumentReference dbRef = db.collection(emailAddr).document(key);
        dbRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                DocumentSnapshot document = task.getResult();
                textView.setText( document.get("Name").toString());
                byte[] decodedBase64String = Base64.decode(document.get("Base64").toString(), Base64.DEFAULT);
                Glide.with(DetailsActivity.this).load(decodedBase64String).into(imageView);
            }
        });

    }
}