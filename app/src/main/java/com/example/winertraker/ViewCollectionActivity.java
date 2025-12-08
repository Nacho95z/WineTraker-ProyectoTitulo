package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;       // ✅ IMPORT PARA ZOOM
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
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

    // 🔍 Overlay y zoom
    private View fullscreenOverlay;        // ✅ overlay negro
    private PhotoView fullscreenImage;     // ✅ imagen con pinch-to-zoom

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
                Intent intent = new Intent(ViewCollectionActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_my_cellar) {
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

        // 🔧 Optimizar rendimiento del scroll
        recyclerView.setHasFixedSize(true);        // si los items no cambian de tamaño
        recyclerView.setItemViewCacheSize(20);     // mantiene vistas ya infladas en memoria
        // recyclerView.setItemAnimator(null);     // opcional, si notas tirones al borrar/editar

        // 🔍 Overlay y PhotoView
        fullscreenOverlay = findViewById(R.id.fullscreenOverlay);
        fullscreenImage = findViewById(R.id.fullscreenImage);

        // Cerrar al tocar el overlay (por si hay zona libre)
        if (fullscreenOverlay != null) {
            fullscreenOverlay.setOnClickListener(v -> closeFullImage());
        }

        // Cerrar al tocar la imagen (tap simple)
        // Usamos el listener propio de PhotoView para taps
        if (fullscreenImage != null) {
            fullscreenImage.setOnViewTapListener((view, x, y) -> closeFullImage());
        }


        collectionList = new ArrayList<>();

        // ✅ Pasamos un callback al adapter para el click en la imagen
        adapter = new CollectionAdapter(collectionList, userId, imageUrl -> showFullImage(imageUrl));
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

    // 🔍 Mostrar imagen en grande con fade-in
    private void showFullImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty() || fullscreenOverlay == null || fullscreenImage == null) {
            return;
        }

        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.setAlpha(0f);

        // Carga la imagen (Picasso la toma desde cache si ya se usó antes)
        Picasso.get()
                .load(imageUrl)
                .into(fullscreenImage);

        fullscreenOverlay.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
    }

    private void closeFullImage() {
        if (fullscreenOverlay == null || fullscreenOverlay.getVisibility() != View.VISIBLE) return;

        fullscreenOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    fullscreenOverlay.setVisibility(View.GONE);
                    fullscreenOverlay.setAlpha(1f);
                    fullscreenImage.setImageDrawable(null);
                })
                .start();
    }


    @Override
    public void onBackPressed() {
        // Si el overlay está visible, ciérralo primero
        if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
            closeFullImage();
            return;
        }

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

        interface OnImageClickListener {
            void onImageClick(String imageUrl);
        }

        private final List<CollectionItem> collectionList;
        private final String userId;
        private final OnImageClickListener imageClickListener;   // ✅ callback

        CollectionAdapter(List<CollectionItem> collectionList, String userId,
                          OnImageClickListener imageClickListener) {
            this.collectionList = collectionList;
            this.userId = userId;
            this.imageClickListener = imageClickListener;
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

            // Siempre reseteamos la imagen con un placeholder básico
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Picasso.get()
                        .load(item.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .fit()          // se ajusta al tamaño real del ImageView (100x140dp)
                        .centerCrop()   // mantiene proporción, recortando si es necesario
                        .into(holder.imageView);
            }

            // 🔍 Click en miniatura → mostrar overlay con zoom
            holder.imageView.setOnClickListener(v -> {
                if (imageClickListener != null && item.imageUrl != null && !item.imageUrl.isEmpty()) {
                    imageClickListener.onImageClick(item.imageUrl);
                }
            });

            holder.nameTextView.setText(item.name);
            holder.varietyTextView.setText("Variedad: " + item.variety);
            holder.vintageTextView.setText("Año: " + item.vintage);
            holder.originTextView.setText("Origen: " + item.origin);
            holder.percentageTextView.setText("Alcohol: " + item.percentage);

            // --- Eliminar ---
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

            // --- Editar ---
            holder.editButton.setOnClickListener(v ->
                    showEditDialog(holder.itemView.getContext(), item, position)
            );
        }



        private void showEditDialog(Context context, CollectionItem item, int position) {

            // Inflamos el nuevo layout con estilo tipo login
            View dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_edit_item, null);

            TextInputEditText editName = dialogView.findViewById(R.id.editName);
            TextInputEditText editVariety = dialogView.findViewById(R.id.editVariety);
            TextInputEditText editVintage = dialogView.findViewById(R.id.editVintage);
            TextInputEditText editOrigin = dialogView.findViewById(R.id.editOrigin);
            TextInputEditText editPercentage = dialogView.findViewById(R.id.editPercentage);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnSave = dialogView.findViewById(R.id.btnSave);

            editName.setText(item.name);
            editVariety.setText(item.variety);
            editVintage.setText(item.vintage);
            editOrigin.setText(item.origin);
            editPercentage.setText(item.percentage);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .create();

            // Fondo transparente para respetar bg_dialog_wine
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT)
                );
            }

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                item.name = editName.getText() != null ? editName.getText().toString().trim() : "";
                item.variety = editVariety.getText() != null ? editVariety.getText().toString().trim() : "";
                item.vintage = editVintage.getText() != null ? editVintage.getText().toString().trim() : "";
                item.origin = editOrigin.getText() != null ? editOrigin.getText().toString().trim() : "";
                item.percentage = editPercentage.getText() != null ? editPercentage.getText().toString().trim() : "";

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
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show());
            });

            dialog.show();
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
