package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DetailsActivity extends AppCompatActivity {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ImageView imageView;
    private TextInputEditText nameTextField, relationshipTextField, serverTimeStampTextField;
    private Base64ImageDB database;
    private MaterialToolbar topAppbar;
    private MaterialAlertDialogBuilder materialAlertDialogBuilder;
    private List<String> nameList = new ArrayList<>();


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
        nameTextField = findViewById(R.id.textField);
        relationshipTextField = findViewById(R.id.relationshipTextField);
        serverTimeStampTextField = findViewById(R.id.serverTimeStampTextField);
        topAppbar = findViewById(R.id.topAppBar);
        nameList = getIntent().getStringArrayListExtra("nameList");
        Intent intent = getIntent();
        String key = intent.getStringExtra("key");
        String emailAddr = intent.getStringExtra("emailAddr");
        database = Base64ImageDB.getInstance(getApplicationContext());
        String base64String = database.base64ImageDao().getImage(key);
        DocumentReference dbRef = db.collection(emailAddr).document(key);
        dbRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                DocumentSnapshot document = task.getResult();
                nameTextField.setText( document.get("Name").toString());
                nameTextField.setEnabled(false);
                relationshipTextField.setText(document.get("Relationship").toString());
                relationshipTextField.setEnabled(false);
                Timestamp createdDateTime = (Timestamp) document.get("TimeStamp");
                Date date = createdDateTime.toDate();
                android.text.format.DateFormat dateFormat = new android.text.format.DateFormat();
                serverTimeStampTextField.setText(dateFormat.format("yyyy-MM-dd hh:mm:ss a", date));
                serverTimeStampTextField.setEnabled(false);
                byte[] decodedBase64String = Base64.decode(base64String, Base64.DEFAULT);
                Glide.with(DetailsActivity.this).load(decodedBase64String).centerCrop().into(imageView);
            }
        });

        //Set on Navigation onClickListener here
        topAppbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch(item.getItemId()){
                    case R.id.editIcon:
                        Intent intent1 = new Intent(DetailsActivity.this, EditActivity.class);
                        intent1.putExtra("key",key);
                        intent1.putExtra("emailAddr",emailAddr);
                        intent1.putStringArrayListExtra("nameList", (ArrayList<String>) nameList);
                        //intent1.putExtra("Base64",base64String);
                        startActivity(intent1);
                        return true;

                    case R.id.deleteIcon:
                        //AlertDialog confirm with user if he wants delete
                        materialAlertDialogBuilder = new MaterialAlertDialogBuilder(DetailsActivity.this);
                        materialAlertDialogBuilder
                                .setTitle("Do you wish to delete this item?")
                                .setMessage("Items deleted are non-retrievable")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //delete item from firestore and room DB
                                        dbRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                database.base64ImageDao().deleteSingleItem(key);
                                            }
                                        }).addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                //Do Nothing
                                            }
                                        });
                                        //return back to Album activity

                                        finish();
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                     @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //do nothing
                                    }
                                 }).show();
                        return true;

                    default:
                        return false;
                }
            }
        });
    }
}