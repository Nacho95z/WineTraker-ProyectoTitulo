package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class ViewCollectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CollectionAdapter adapter;
    private List<CollectionItem> collectionList;
    private FirebaseFirestore firestore;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        collectionList = new ArrayList<>();
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user != null ? user.getUid() : "unknown";

        adapter = new CollectionAdapter(collectionList, userId); // Pasamos el userId aquí
        recyclerView.setAdapter(adapter);

        loadCollection();
    }

    private void loadCollection() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading collection...");
        progressDialog.show();

        CollectionReference userCollection = firestore.collection("descriptions").document(userId).collection("wineDescriptions");

        userCollection.get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        collectionList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String documentId = document.getId();
                            String imageUrl = document.getString("imageUrl");
                            String recognizedText = document.getString("recognizedText");
                            collectionList.add(new CollectionItem(documentId, imageUrl, recognizedText));
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "No items found in collection", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to load collection", Toast.LENGTH_SHORT).show();
                });
    }



    private static class CollectionItem {
        String documentId;
        String imageUrl;
        String recognizedText;

        CollectionItem(String documentId, String imageUrl, String recognizedText) {
            this.documentId = documentId;
            this.imageUrl = imageUrl;
            this.recognizedText = recognizedText;
        }
    }

    private static class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {

        private final List<CollectionItem> collectionList;
        private final String userId;

        CollectionAdapter(List<CollectionItem> collectionList, String userId) {
            this.collectionList = collectionList;
            this.userId = userId;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_collection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CollectionItem item = collectionList.get(position);
            Picasso.get().load(item.imageUrl).into(holder.imageView);
            holder.textView.setText(item.recognizedText);

            holder.deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete this item?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                            firestore.collection("descriptions").document(userId).collection("wineDescriptions")
                                    .document(item.documentId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        collectionList.remove(position);
                                        notifyItemRemoved(position);
                                        Toast.makeText(holder.itemView.getContext(), "Item deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(holder.itemView.getContext(), "Failed to delete item", Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
        }


        @Override
        public int getItemCount() {
            return collectionList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textView;
            Button deleteButton;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.itemImageView);
                textView = itemView.findViewById(R.id.itemDescription);
                deleteButton = itemView.findViewById(R.id.buttonDelete);
            }
        }
    }
}
