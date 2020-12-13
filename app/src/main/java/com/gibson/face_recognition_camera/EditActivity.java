package com.gibson.face_recognition_camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EditActivity extends AppCompatActivity {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ImageView imageView;
    private TextInputEditText nameTextField, relationshipTextField, serverTimeStampTextField;
    private MaterialToolbar topAppbar;
    private Base64ImageDB database;
    private String newName, newRelationship;
    private List<String> nameList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        imageView = findViewById(R.id.orgImageviewEdit);
        nameTextField = findViewById(R.id.textFieldEditName);
        relationshipTextField = findViewById(R.id.relationshipTextFieldEdit);
        serverTimeStampTextField = findViewById(R.id.serverTimeStampTextFieldEdit);
        topAppbar = findViewById(R.id.topAppBar2);
        nameList = getIntent().getStringArrayListExtra("nameList");
        Intent intent = getIntent();
        String key = intent.getStringExtra("key");
        String emailAddr = intent.getStringExtra("emailAddr");
        //String base64String = intent.getStringExtra("Base64String");
        database = Base64ImageDB.getInstance(getApplicationContext());
        String base64String = database.base64ImageDao().getImage(key);
        DocumentReference dbRef = db.collection(emailAddr).document(key);
        dbRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                DocumentSnapshot document = task.getResult();
                nameTextField.setText( document.get("Name").toString());
                relationshipTextField.setText(document.get("Relationship").toString());
                Timestamp createdDateTime = (Timestamp) document.get("TimeStamp");
                Date date = createdDateTime.toDate();
                android.text.format.DateFormat dateFormat = new android.text.format.DateFormat();
                serverTimeStampTextField.setText(dateFormat.format("yyyy-MM-dd hh:mm:ss a", date));
                serverTimeStampTextField.setEnabled(false);
                byte[] decodedBase64String = Base64.decode(base64String, Base64.DEFAULT);
                Glide.with(EditActivity.this).load(decodedBase64String).centerCrop().into(imageView);
            }
        });

        topAppbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        topAppbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()){
                    case R.id.check_icon:
                        //update the firestore with the new name and relationship
                        newName = nameTextField.getText().toString();
                        newRelationship = relationshipTextField.getText().toString();
                        dbRef.update("Name", newName, "RelationShip", newRelationship);
                        Intent backIntent = new Intent(EditActivity.this, AlbumActivity.class);
                        backIntent.putStringArrayListExtra("nameList", (ArrayList<String>) nameList);
                        backIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(backIntent);
                }
                return false;
            }
        });
    }


}