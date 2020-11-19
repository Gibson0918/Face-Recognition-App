package com.gibson.face_recognition_camera;

import android.content.Context;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

public class FaceAdapter extends FirestoreRecyclerAdapter<Face, FaceAdapter.FaceHolder> {

    /**
     * Create a new RecyclerView adapter that listens to a Firestore Query.  See {@link
     * FirestoreRecyclerOptions} for configuration options.
     *
     * @param options
     */

    private Context context;
    private OnItemClickListener mListener;

    public interface OnItemClickListener {
        void onItemClick(String key);
    }

    public void setOnItemClickListener(OnItemClickListener listener) { mListener = listener;}

    public FaceAdapter(@NonNull FirestoreRecyclerOptions<Face> options, Context context) {
        super(options);
        this.context = context;
    }

    @Override
    public FaceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.face_img_circle, parent, false);
        return new FaceHolder(v, mListener, context);
    }

    @Override
    protected void onBindViewHolder(@NonNull FaceAdapter.FaceHolder holder, int position, @NonNull Face model) {
        byte[] decodedBase64String = Base64.decode(model.getBase64(), Base64.DEFAULT);
        Glide.with(holder.shapeableImageView.getContext()).load(decodedBase64String).into(holder.shapeableImageView);
        holder.textView.setText(model.getName());
    }

    class FaceHolder extends RecyclerView.ViewHolder {

        private ShapeableImageView shapeableImageView;
        private TextView textView;
        private ConstraintLayout circleFaceConstraintLayout;

        public FaceHolder(@NonNull View itemView, final OnItemClickListener listener, Context context) {
            super(itemView);
            shapeableImageView = itemView.findViewById(R.id.item_poster_post);
            textView = itemView.findViewById(R.id.textViewName);
            circleFaceConstraintLayout = itemView.findViewById(R.id.face_poster_constraintLayout);
            circleFaceConstraintLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null ) {
                        int pos = getAdapterPosition();
                        String key = getSnapshots().getSnapshot(pos).getId();
                        listener.onItemClick(key);

                    }
                }
            });
        }
    }
}
