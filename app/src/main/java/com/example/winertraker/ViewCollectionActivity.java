package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton; // <--- IMPORTANTE: Importar ImageButton
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

        // 1. Ocultar la barra de acción por defecto para usar tu diseño personalizado
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 2. Configurar el botón de atrás (backButton)
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            finish(); // Cierra esta pantalla y vuelve al menú anterior
        });

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
        progressDialog.setMessage("Cargando colección..."); // Traduje el mensaje para consistencia
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
                            String name = document.getString("wineName");
                            String variety = document.getString("variety");
                            String vintage = document.getString("vintage");
                            String origin = document.getString("origin");
                            String percentage = document.getString("percentage");
                            boolean isOptimal = isOptimalForConsumption(variety, vintage);
                            collectionList.add(new CollectionItem(documentId, imageUrl, name, variety, vintage, origin, percentage, isOptimal));
                        }
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "No se encontraron vinos en la colección", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error al cargar la colección", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isOptimalForConsumption(String variety, String vintageStr) {
        if (variety == null || vintageStr == null) return false;

        try {
            int vintageYear = Integer.parseInt(vintageStr);
            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            int wineAge = currentYear - vintageYear;

            switch (variety.toLowerCase()) {
                case "pinot noir":
                case "gamay":
                    return wineAge >= 2 && wineAge <= 5;
                case "merlot":
                case "tempranillo":
                    return wineAge >= 5 && wineAge <= 10;
                case "cabernet sauvignon":
                case "syrah":
                    return wineAge >= 10 && wineAge <= 20;
                case "sauvignon blanc":
                case "riesling":
                    return wineAge >= 1 && wineAge <= 3;
                case "chardonnay":
                case "viognier":
                    return wineAge >= 5 && wineAge <= 8;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static class CollectionItem {
        String documentId;
        String imageUrl;
        String name;
        String variety;
        String vintage;
        String origin;
        String percentage;
        boolean isOptimal;

        CollectionItem(String documentId, String imageUrl, String name, String variety, String vintage, String origin, String percentage, boolean isOptimal) {
            this.documentId = documentId;
            this.imageUrl = imageUrl;
            this.name = name != null ? name : "No disponible";
            this.variety = variety != null ? variety : "No disponible";
            this.vintage = vintage != null ? vintage : "No disponible";
            this.origin = origin != null ? origin : "No disponible";
            this.percentage = percentage != null ? percentage : "No disponible";
            this.isOptimal = isOptimal;
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

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Picasso.get().load(item.imageUrl).placeholder(android.R.drawable.ic_menu_gallery).into(holder.imageView);
            }

            holder.nameTextView.setText(item.name);
            holder.varietyTextView.setText("Variedad: " + item.variety);
            holder.vintageTextView.setText("Año: " + item.vintage);
            holder.originTextView.setText("Origen: " + item.origin);
            holder.percentageTextView.setText("Alcohol: " + item.percentage);

            // Resaltar los vinos óptimos (Opcional: Puedes cambiar esto si el diseño de tarjeta ya es suficiente)
            /* if (item.isOptimal) {
                holder.itemView.setBackgroundColor(holder.itemView.getContext().getResources().getColor(R.color.optimalHighlight));
            }
            */

            holder.deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Eliminar Vino")
                        .setMessage("¿Estás seguro de que deseas eliminar este vino?")
                        .setPositiveButton("Sí", (dialog, which) -> {
                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                            firestore.collection("descriptions").document(userId).collection("wineDescriptions")
                                    .document(item.documentId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        collectionList.remove(position);
                                        notifyItemRemoved(position);
                                        // notifyItemRangeChanged es importante para actualizar posiciones
                                        notifyItemRangeChanged(position, collectionList.size());
                                        Toast.makeText(holder.itemView.getContext(), "Vino eliminado", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(holder.itemView.getContext(), "Error al eliminar", Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .setNegativeButton("No", null)
                        .show();
            });

            holder.editButton.setOnClickListener(v -> {
                showEditDialog(holder.itemView.getContext(), item, position);
            });
        }

        private void showEditDialog(Context context, CollectionItem item, int position) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Editar información");

            // Crear el layout para el diálogo
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_item, null);
            EditText editName = dialogView.findViewById(R.id.editName);
            EditText editVariety = dialogView.findViewById(R.id.editVariety);
            EditText editVintage = dialogView.findViewById(R.id.editVintage);
            EditText editOrigin = dialogView.findViewById(R.id.editOrigin);
            EditText editPercentage = dialogView.findViewById(R.id.editPercentage);

            // Rellenar los campos con la información actual
            editName.setText(item.name);
            editVariety.setText(item.variety);
            editVintage.setText(item.vintage);
            editOrigin.setText(item.origin);
            editPercentage.setText(item.percentage);

            builder.setView(dialogView);

            builder.setPositiveButton("Guardar", (dialog, which) -> {
                item.name = editName.getText().toString().trim();
                item.variety = editVariety.getText().toString().trim();
                item.vintage = editVintage.getText().toString().trim();
                item.origin = editOrigin.getText().toString().trim();
                item.percentage = editPercentage.getText().toString().trim();

                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                firestore.collection("descriptions").document(userId)
                        .collection("wineDescriptions").document(item.documentId)
                        .update("wineName", item.name,
                                "variety", item.variety,
                                "vintage", item.vintage,
                                "origin", item.origin,
                                "percentage", item.percentage)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show();
                            notifyItemChanged(position);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show();
                        });
            });

            builder.setNegativeButton("Cancelar", null);
            builder.create().show();
        }

        @Override
        public int getItemCount() {
            return collectionList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView nameTextView, varietyTextView, vintageTextView, originTextView, percentageTextView;
            Button deleteButton, editButton;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.itemImageView);
                nameTextView = itemView.findViewById(R.id.itemName);
                varietyTextView = itemView.findViewById(R.id.itemVariety);
                vintageTextView = itemView.findViewById(R.id.itemVintage);
                originTextView = itemView.findViewById(R.id.itemOrigin);
                percentageTextView = itemView.findViewById(R.id.itemPercentage);
                deleteButton = itemView.findViewById(R.id.buttonDelete);
                editButton = itemView.findViewById(R.id.buttonEdit);
            }
        }
    }
}