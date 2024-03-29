package com.gibson.face_recognition_camera;

import android.content.Context;
import android.database.CursorWindow;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.paging.FirestorePagingAdapter;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.firebase.ui.firestore.paging.LoadingState;
import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.Field;

public class FaceAdapter extends FirestorePagingAdapter<Face, FaceAdapter.FaceHolder> {

    private Context context;
    private OnItemClickListener mListener;
    private ProgressBar progressBar;
    private ConstraintLayout constraintLayout;
    private RecyclerView recyclerView;
    private Base64ImageDB database;

    public interface OnItemClickListener {
        void onItemClick(String key);
    }

    public void setOnItemClickListener(OnItemClickListener listener) { mListener = listener;}

    public FaceAdapter(@NonNull FirestorePagingOptions<Face> options, Context context, ProgressBar progressBar, ConstraintLayout constraintLayout, RecyclerView recyclerView) {
        super(options);
        this.context = context;
        this.progressBar = progressBar;
        this.constraintLayout = constraintLayout;
        this.recyclerView = recyclerView;
    }

    @Override
    public FaceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.face_img_circle, parent, false);
        return new FaceHolder(v, mListener, context);
    }

    @Override
    protected void onBindViewHolder(@NonNull FaceAdapter.FaceHolder holder, int position, @NonNull Face model) {
        //byte[] decodedBase64String = Base64.decode(model.getBase64(), Base64.DEFAULT);
        //Glide.with(holder.imageView.getContext()).load(decodedBase64String).centerCrop().into(holder.imageView);
        holder.textView.setText(model.getName());
        holder.relationshipTextView.setText(model.getRelationship());
        try{
            Field field = CursorWindow.class.getDeclaredField("sCursorWindowSize");
            field.setAccessible(true);
            field.set(null, 100 * 1024 *1024); //Set size to 100MB per cursor
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        database = Base64ImageDB.getInstance(context);

        String id = getItem(position).getId();
        String Base64String = database.base64ImageDao().getImage(getItem(position).getId());
        String id2 = getItem(position).getId();
        if(!(Base64String == null)) {
            byte[] decodedBase64String = Base64.decode(Base64String, Base64.DEFAULT);
            Glide.with(holder.imageView.getContext()).load(decodedBase64String).thumbnail(0.6f).centerCrop().into(holder.imageView);
        }
        else {
            Glide.with(holder.imageView.getContext()).load(R.drawable.white_add_person_icon).into(holder.imageView);
        }
        //holder.circleFaceConstraintLayout.setTransitionName(String.valueOf(position));
    }

    @Override
    protected void onLoadingStateChanged(@NonNull LoadingState state){
        super.onLoadingStateChanged(state);
        switch (state) {
            case LOADED:
                Log.d("PAGING_LOG", "Total item loaded:" +getItemCount());
                progressBar.setVisibility(View.INVISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
                break;
            case FINISHED:
                Log.d("PAGING_LOG", "All item loaded");
                progressBar.setVisibility(View.INVISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                Log.d("PAGING_LOG", "Error loading items");
                progressBar.setVisibility(View.INVISIBLE);
                Snackbar.make(constraintLayout, "Error loading items, Please try again!", Snackbar.LENGTH_LONG).show();
                break;
            case LOADING_MORE:
                Log.d("PAGING_LOG", "Loading next page");
                progressBar.setVisibility(View.INVISIBLE);
                break;
            case LOADING_INITIAL:
                Log.d("PAGING_LOG", "Loading initial data");
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    class FaceHolder extends RecyclerView.ViewHolder {

        private ImageView imageView;
        private TextView textView;
        private ConstraintLayout circleFaceConstraintLayout;
        private  TextView relationshipTextView;

        public FaceHolder(@NonNull View itemView, final OnItemClickListener listener, Context context) {
            super(itemView);
            imageView = itemView.findViewById(R.id.faceImageView);
            textView = itemView.findViewById(R.id.NameTxt);
            relationshipTextView = itemView.findViewById(R.id.relationshipTxt);
            circleFaceConstraintLayout = itemView.findViewById(R.id.face_poster_constraintLayout);
            circleFaceConstraintLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null ) {
                        int pos = getAdapterPosition();
                        String key = getItem(pos).getId();
                        Log.d("key",key);
                        listener.onItemClick(key);
                    }
                }
            });
        }
    }
}
