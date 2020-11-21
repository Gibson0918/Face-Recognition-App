package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class EditActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference dbRef;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String emailAddr;
    private FaceAdapter faceAdapter;
    private List<String> nameList = new ArrayList<>();
    private Spinner spinner;
    private ArrayAdapter<String> adapter;
    private Query query;
    private ProgressBar progressBar;
    private ConstraintLayout constraintLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
//        findViewById(android.R.id.content).setTransitionName("shared_element_container");
//        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
//        MaterialContainerTransform transform = new MaterialContainerTransform();
//        transform.addTarget(android.R.id.content);
//        //transform.setAllContainerColors(MaterialColors.getColor(findViewById(android.R.id.content), R.attr.colorSurface));
//        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
//        transform.setDuration(550L);
//        transform.setPathMotion(new MaterialArcMotion());
//        transform.setInterpolator(new FastOutSlowInInterpolator());
//        getWindow().setSharedElementEnterTransition(transform);
//        getWindow().setSharedElementExitTransition(transform);
//        getWindow().setSharedElementReenterTransition(transform);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        emailAddr = currentUser.getEmail();
        dbRef = db.collection(emailAddr);
        progressBar = findViewById(R.id.progressBar);
        constraintLayout = findViewById(R.id.AlbumConstraintLayout);

        setupRecyclerView(progressBar, constraintLayout);
    }

    public void setupRecyclerView(ProgressBar progressBar, ConstraintLayout constraintLayout) {

        nameList = getIntent().getStringArrayListExtra("nameList");
        spinner = findViewById(R.id.spinner);
        adapter = new ArrayAdapter(EditActivity.this, R.layout.custom_spinner, nameList);
        adapter.notifyDataSetChanged();
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(EditActivity.this);


        Query query = dbRef.orderBy("Name", Query.Direction.DESCENDING);

        PagedList.Config config = new PagedList.Config.Builder()
                .setInitialLoadSizeHint(1)
                .setPageSize(1)
                .build();

        FirestorePagingOptions<Face> options = new FirestorePagingOptions.Builder<Face>()
                .setQuery(query, config ,Face.class)
                .build();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        faceAdapter = new FaceAdapter(options, this, progressBar, constraintLayout, recyclerView);
        recyclerView.setHasFixedSize(true);
        // already set layoutmanager in the xml file
        recyclerView.setAdapter(faceAdapter);

        faceAdapter.setOnItemClickListener(new FaceAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String key) {
                Intent intent = new Intent(EditActivity.this, DetailsActivity.class);
                intent.putExtra("key", key);
                intent.putExtra("emailAddr", emailAddr);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        faceAdapter.startListening();
    }

    @Override
    protected void onStop(){
        super.onStop();
        faceAdapter.stopListening();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String choice = parent.getItemAtPosition(position).toString();
        Log.d("choice",choice);

        if(choice.equals("Show All")) {
            query = dbRef.orderBy("Name", Query.Direction.ASCENDING);
            PagedList.Config config = new PagedList.Config.Builder()
                    .setInitialLoadSizeHint(8)
                    .setPageSize(6)
                    .build();

            FirestorePagingOptions<Face> newOptions = new FirestorePagingOptions.Builder<Face>()
                    .setQuery(query, config ,Face.class)
                    .build();
            faceAdapter.updateOptions(newOptions);
        }
        else {
            query = dbRef.whereEqualTo("Name",choice);
            PagedList.Config config = new PagedList.Config.Builder()
                    .setInitialLoadSizeHint(8)
                    .setPageSize(6)
                    .build();

            FirestorePagingOptions<Face> newOptions = new FirestorePagingOptions.Builder<Face>()
                    .setQuery(query, config ,Face.class)
                    .build();
            faceAdapter.updateOptions(newOptions);
        }

        FirestoreRecyclerOptions<Face> options = new FirestoreRecyclerOptions.Builder<Face>()
                .setQuery(query, Face.class)
                .build();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}