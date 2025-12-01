package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class ViewCollectionActivity extends AppCompatActivity {

    // Drawer
    private DrawerLayout drawerLayoutCollection;
    private NavigationView navigationView;
    private ImageView menuIcon;

    private RecyclerView recyclerView;
    private CollectionAdapter adapter;
    private List<CollectionItem> collectionList;
    private FirebaseFirestore firestore;
    private FirebaseUser user;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);

        // Ocultar ActionBar para usar nuestro header custom
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Firebase
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        userId = (user != null) ? user.getUid() : null;

        // Drawer + menú
        drawerLayoutCollection = findViewById(R.id.drawerLayoutCollection);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        // Abrir drawer al tocar el ícono
        menuIcon.setOnClickListener(v ->
                drawerLayoutCollection.openDrawer(GravityCompat.START)
        );

        // Rellenar header con nombre/correo
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            TextView headerTitle = headerView.findViewById(R.id.headerTitle);
            TextView headerEmail = headerView.findViewById(R.id.headerEmail);

            if (user != null) {
                String displayName = user.getDisplayName();
                if (displayName == null || displayName.isEmpty()) {
                    displayName = "Amante del vino";
                }
                headerTitle.setText(displayName);
                headerEmail.setText(user.getEmail());
            }
        }

        // Manejar clics del menú lateral
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                // Ir al Home
                Intent intent = new Intent(ViewCollectionActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_my_cellar) {
                // Ya estamos en Mi Bodega → solo cerrar menú
                drawerLayoutCollection.closeDrawer(GravityCompat.START);

            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(ViewCollectionActivity.this, SettingsActivity.class));

            } else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("remember_session", false)
                        .apply();
                Intent intent = new Intent(ViewCollectionActivity.this, AuthActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }

            drawerLayoutCollection.closeDrawer(GravityCompat.START);
            return true;
        });

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        collectionList = new ArrayList<>();
        adapter = new CollectionAdapter(collectionList, userId);
        recyclerView.setAdapter(adapter);

        if (userId != null) {
            loadCollection();
        } else {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCollection() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Cargando colección...");
        progressDialog.show();

        CollectionReference userCollection = firestore
                .collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        userCollection.get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
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
                            collectionList.add(new CollectionItem(
                                    documentId, imageUrl, name, variety, vintage, origin, percentage, isOptimal
                            ));
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

    // Cerrar drawer con back si está abierto
    @Override
    public void onBackPressed() {
        if (drawerLayoutCollection != null &&
                drawerLayoutCollection.isDrawerOpen(GravityCompat.START)) {
            drawerLayoutCollection.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // --------- MODELO Y ADAPTER ---------

    private static class CollectionItem {
        String documentId;
        String imageUrl;
        String name;
        String variety;
        String vintage;
        String origin;
        String percentage;
        boolean isOptimal;

        CollectionItem(String documentId, String imageUrl, String name,
                       String variety, String vintage, String origin,
                       String percentage, boolean isOptimal) {

            this.documentId = documentId;
            this.imageUrl = imageUrl;
            this.name = (name != null) ? name : "No disponible";
            this.variety = (variety != null) ? variety : "No disponible";
            this.vintage = (vintage != null) ? vintage : "No disponible";
            this.origin = (origin != null) ? origin : "No disponible";
            this.percentage = (percentage != null) ? percentage : "No disponible";
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
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_collection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CollectionItem item = collectionList.get(position);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Picasso.get()
                        .load(item.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.imageView);
            }

            holder.nameTextView.setText(item.name);
            holder.varietyTextView.setText("Variedad: " + item.variety);
            holder.vintageTextView.setText("Año: " + item.vintage);
            holder.originTextView.setText("Origen: " + item.origin);
            holder.percentageTextView.setText("Alcohol: " + item.percentage);

            holder.deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Eliminar vino")
                        .setMessage("¿Estás seguro de que deseas eliminar este vino?")
                        .setPositiveButton("Sí", (dialog, which) -> {
                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                            firestore.collection("descriptions")
                                    .document(userId)
                                    .collection("wineDescriptions")
                                    .document(item.documentId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        collectionList.remove(position);
                                        notifyItemRemoved(position);
                                        notifyItemRangeChanged(position, collectionList.size());
                                        Toast.makeText(holder.itemView.getContext(),
                                                "Vino eliminado", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(holder.itemView.getContext(),
                                                    "Error al eliminar", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("No", null)
                        .show();
            });

            holder.editButton.setOnClickListener(v ->
                    showEditDialog(holder.itemView.getContext(), item, position)
            );
        }

        private void showEditDialog(Context context, CollectionItem item, int position) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Editar información");

            View dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_edit_item, null);

            EditText editName = dialogView.findViewById(R.id.editName);
            EditText editVariety = dialogView.findViewById(R.id.editVariety);
            EditText editVintage = dialogView.findViewById(R.id.editVintage);
            EditText editOrigin = dialogView.findViewById(R.id.editOrigin);
            EditText editPercentage = dialogView.findViewById(R.id.editPercentage);

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
                firestore.collection("descriptions")
                        .document(userId)
                        .collection("wineDescriptions")
                        .document(item.documentId)
                        .update("wineName", item.name,
                                "variety", item.variety,
                                "vintage", item.vintage,
                                "origin", item.origin,
                                "percentage", item.percentage)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show();
                            notifyItemChanged(position);
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show());
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