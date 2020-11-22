package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


public class DetailsActivity extends AppCompatActivity {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ImageView imageView;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        findViewById(android.R.id.content).setTransitionName(getIntent().getStringExtra("transitionName"));
//        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
//        MaterialContainerTransform transform = new MaterialContainerTransform();
//        transform.addTarget(android.R.id.content);
//       // transform.setAllContainerColors(MaterialColors.getColor(findViewById(android.R.id.content), R.attr.colorSurface));
//        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
//        transform.setDuration(550L);
//        transform.setPathMotion(new MaterialArcMotion());
//        transform.setInterpolator(new FastOutSlowInInterpolator());
//        getWindow().setSharedElementEnterTransition(transform);
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