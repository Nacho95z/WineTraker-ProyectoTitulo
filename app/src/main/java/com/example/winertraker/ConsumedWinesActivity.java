package com.example.winertraker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConsumedWinesActivity extends AppCompatActivity {

    // Drawer
    private DrawerLayout drawerLayoutCollection;
    private NavigationView navigationView;
    private ImageView menuIcon;

    // Lista
    private RecyclerView recyclerView;
    private ConsumedAdapter adapter;
    private final List<ConsumedItem> consumedList = new ArrayList<>();
    private final List<ConsumedItem> fullConsumedList = new ArrayList<>();

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseUser user;
    private String userId;

    // Fullscreen zoom
    private View fullscreenOverlay;
    private PhotoView fullscreenImage;

    // Filtros
    private Spinner spinnerField;
    private EditText editFilterValue;

    // Campos disponibles para filtrar
    private final Set<String> availableFilterKeys = new LinkedHashSet<>();
    private final List<String> filterFieldKeys = new ArrayList<>(); // keys reales

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);

        // Título header
        TextView title = findViewById(R.id.titleText);
        if (title != null) title.setText("Consumidos");

        // Ocultar fila "apogeo" (texto + switch)
        View optimalRow = findViewById(R.id.optimalToggleRow);
        if (optimalRow != null) optimalRow.setVisibility(View.GONE);

        // Ocultar ActionBar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Firebase
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        userId = (user != null) ? user.getUid() : null;

        // Drawer + menú
        drawerLayoutCollection = findViewById(R.id.drawerLayoutCollection);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        if (menuIcon != null) {
            menuIcon.setOnClickListener(v ->
                    drawerLayoutCollection.openDrawer(GravityCompat.START)
            );
        }

        // Header drawer (nombre/correo)
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            TextView headerTitle = headerView.findViewById(R.id.headerTitle);
            TextView headerEmail = headerView.findViewById(R.id.headerEmail);

            if (user != null) {
                String displayName = user.getDisplayName();
                if (displayName == null || displayName.isEmpty()) displayName = "Amante del vino";
                headerTitle.setText(displayName);
                headerEmail.setText(user.getEmail());
            }
        }

        // Clicks menú
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    Intent intent = new Intent(ConsumedWinesActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);

                } else if (id == R.id.nav_my_cellar) {
                    Intent intent = new Intent(ConsumedWinesActivity.this, ViewCollectionActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);

                } else if (id == R.id.nav_consumed) {
                    drawerLayoutCollection.closeDrawer(GravityCompat.START);
                    return true;

                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(ConsumedWinesActivity.this, SettingsActivity.class));

                } else if (id == R.id.nav_logout) {
                    FirebaseAuth.getInstance().signOut();
                    getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("remember_session", false)
                            .apply();

                    Intent intent = new Intent(ConsumedWinesActivity.this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }

                drawerLayoutCollection.closeDrawer(GravityCompat.START);
                return true;
            });
        }


        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);

        // Overlay zoom
        fullscreenOverlay = findViewById(R.id.fullscreenOverlay);
        fullscreenImage = findViewById(R.id.fullscreenImage);

        if (fullscreenOverlay != null) {
            fullscreenOverlay.setOnClickListener(v -> closeFullImage());
        }
        if (fullscreenImage != null) {
            fullscreenImage.setOnViewTapListener((view, x, y) -> closeFullImage());
        }

        // Filtros
        spinnerField = findViewById(R.id.spinnerField);
        editFilterValue = findViewById(R.id.editFilterValue);

        if (spinnerField != null) {
            spinnerField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String currentText = (editFilterValue.getText() != null)
                            ? editFilterValue.getText().toString().trim()
                            : "";
                    applyFilter(currentText);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    applyFilter("");
                }
            });
        }

        if (editFilterValue != null) {
            editFilterValue.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s.toString().trim());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        }

        // Adapter
        adapter = new ConsumedAdapter(consumedList, userId, this::showFullImage);
        recyclerView.setAdapter(adapter);

        if (userId != null) loadConsumed();
        else Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
    }

    private void loadConsumed() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Cargando consumidos...");
        progressDialog.show();

        CollectionReference userCollection = firestore
                .collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        availableFilterKeys.clear();
        fullConsumedList.clear();
        consumedList.clear();

        userCollection.get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {

                        for (QueryDocumentSnapshot document : task.getResult()) {

                            // ✅ SOLO archivados
                            Boolean archived = document.getBoolean("archived");
                            if (archived == null || !archived) continue;

                            String documentId = document.getId();

                            Map<String, Object> data = document.getData();
                            if (data == null) data = new HashMap<>();

                            // ✅ Fecha archivado
                            com.google.firebase.Timestamp archivedTs = document.getTimestamp("archivedAt");
                            String archivedAtText = "Consumido: Sin fecha";
                            if (archivedTs != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                                archivedAtText = "Consumido: " + sdf.format(archivedTs.toDate());
                            }
                            data.put("archivedAtText", archivedAtText);

                            // imageUrl (fallback)
                            String imageUrl = document.getString("imageUrl");
                            if (imageUrl == null || imageUrl.isEmpty()) {
                                Object rawUrl = data.get("imageUrl");
                                if (rawUrl != null) imageUrl = rawUrl.toString();
                            }

                            // Campos principales
                            String name = document.getString("wineName");
                            String variety = document.getString("variety");
                            String vintage = document.getString("vintage");
                            String origin = document.getString("origin");
                            String percentage = document.getString("percentage");
                            String category = document.getString("category");
                            String comment = document.getString("comment");

                            // price puede venir num o string
                            String price = null;
                            Object rawPrice = document.get("price");
                            if (rawPrice != null) price = rawPrice.toString();

                            ConsumedItem item = new ConsumedItem(
                                    documentId, imageUrl, name, variety, vintage,
                                    origin, percentage, category, comment, price, data
                            );

                            fullConsumedList.add(item);
                            consumedList.add(item);

                            // Keys filtrables
                            for (String key : data.keySet()) {
                                if (shouldIncludeFieldForFilter(key)) {
                                    availableFilterKeys.add(key);
                                }
                            }
                        }

                        setupFilterSpinner();

                        String currentText = (editFilterValue.getText() != null)
                                ? editFilterValue.getText().toString().trim()
                                : "";
                        applyFilter(currentText);

                        if (consumedList.isEmpty()) {
                            Toast.makeText(this, "No se encontraron vinos consumidos", Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Toast.makeText(this, "No se encontraron vinos consumidos", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error al cargar consumidos", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean shouldIncludeFieldForFilter(String key) {
        if (key == null) return false;
        String k = key.trim().toLowerCase();

        // excluye “técnicos”
        if (k.equals("rawtext") || k.equals("raw_text") || k.equals("rawtextfull")) return false;
        if (k.equals("comment")) return false;
        if (k.equals("imageurl") || k.equals("image_url")) return false;
        if (k.equals("createdat") || k.equals("updatedat")) return false;
        if (k.equals("recognizedtext")) return false;

        return true;
    }

    private void setupFilterSpinner() {
        filterFieldKeys.clear();
        if (spinnerField == null) return;

        List<String> labels = new ArrayList<>();

        // 0 = Todos
        filterFieldKeys.add(null);
        labels.add("Todos");

        for (String key : availableFilterKeys) {
            filterFieldKeys.add(key);
            labels.add(getDisplayNameForField(key));
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                labels
        );
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerField.setAdapter(spinnerAdapter);
    }

    private String getDisplayNameForField(String key) {
        if (key == null) return "";
        switch (key) {
            case "wineName":
                return "Nombre";
            case "variety":
                return "Variedad";
            case "vintage":
                return "Cosecha";
            case "origin":
                return "Origen / Región";
            case "percentage":
                return "Grado alcohólico";
            case "category":
                return "Categoría";
            case "price":
                return "Precio";
            case "archivedAtText":
                return "Fecha consumido";
            default:
                return key.substring(0, 1).toUpperCase() + key.substring(1);
        }
    }

    private void applyFilter(String value) {
        value = (value != null) ? value.trim() : "";
        consumedList.clear();

        if (value.isEmpty()) {
            consumedList.addAll(fullConsumedList);
            adapter.notifyDataSetChanged();
            return;
        }

        if (availableFilterKeys.isEmpty()) {
            consumedList.addAll(fullConsumedList);
            adapter.notifyDataSetChanged();
            return;
        }

        String valueLower = value.toLowerCase();

        int pos = (spinnerField != null)
                ? spinnerField.getSelectedItemPosition()
                : Spinner.INVALID_POSITION;

        // Caso 1: Todos -> búsqueda global
        if (pos == Spinner.INVALID_POSITION || pos == 0 || filterFieldKeys.isEmpty()) {
            for (ConsumedItem item : fullConsumedList) {
                if (item.allFields == null) continue;

                boolean matches = false;
                for (String key : availableFilterKeys) {
                    Object raw = item.allFields.get(key);
                    if (raw != null && raw.toString().toLowerCase().contains(valueLower)) {
                        matches = true;
                        break;
                    }
                }
                if (matches) consumedList.add(item);
            }
        } else {
            // Caso 2: campo específico
            String selectedKey = filterFieldKeys.get(pos);

            for (ConsumedItem item : fullConsumedList) {
                if (item.allFields == null) continue;

                Object raw = item.allFields.get(selectedKey);
                if (raw == null) continue;

                String text = raw.toString();

                if ("percentage".equals(selectedKey) || "vintage".equals(selectedKey) || "price".equals(selectedKey)) {
                    String cleanField = text.replaceAll("[^0-9]", "");
                    String cleanFilter = value.replaceAll("[^0-9]", "");
                    if (!cleanFilter.isEmpty() && cleanField.equals(cleanFilter)) {
                        consumedList.add(item);
                    }
                } else {
                    if (text.toLowerCase().contains(valueLower)) {
                        consumedList.add(item);
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    // Fullscreen
    private void showFullImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()
                || fullscreenOverlay == null || fullscreenImage == null) return;

        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.setAlpha(0f);

        PicassoClient.getInstance(fullscreenImage.getContext())
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .fit()
                .centerInside()
                .into(fullscreenImage);

        fullscreenOverlay.animate().alpha(1f).setDuration(200).start();
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
        if (fullscreenOverlay != null && fullscreenOverlay.getVisibility() == View.VISIBLE) {
            closeFullImage();
            return;
        }

        if (drawerLayoutCollection != null && drawerLayoutCollection.isDrawerOpen(GravityCompat.START)) {
            drawerLayoutCollection.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ===== Modelo =====

    private static class ConsumedItem {
        String documentId;
        String imageUrl;
        String name;
        String variety;
        String vintage;
        String origin;
        String percentage;
        String category;
        String comment;
        String price;

        Map<String, Object> allFields;

        ConsumedItem(String documentId, String imageUrl, String name,
                     String variety, String vintage, String origin,
                     String percentage, String category, String comment, String price,
                     Map<String, Object> allFields) {

            this.documentId = documentId;
            this.imageUrl = imageUrl;
            this.name = (name != null) ? name : "No disponible";
            this.variety = (variety != null) ? variety : "No disponible";
            this.vintage = (vintage != null) ? vintage : "No disponible";
            this.origin = (origin != null) ? origin : "No disponible";
            this.percentage = (percentage != null) ? percentage : "No disponible";
            this.category = (category != null) ? category : "No disponible";
            this.comment = (comment != null) ? comment : "Sin comentario del asistente";
            this.price = (price != null) ? price : "No disponible";
            this.allFields = (allFields != null) ? allFields : new HashMap<>();
        }
    }

    // ===== Adapter =====

    private static class ConsumedAdapter extends RecyclerView.Adapter<ConsumedAdapter.ViewHolder> {

        interface OnImageClickListener {
            void onImageClick(String imageUrl);
        }

        private final List<ConsumedItem> list;
        private final String userId;
        private final OnImageClickListener imageClickListener;

        ConsumedAdapter(List<ConsumedItem> list, String userId, OnImageClickListener imageClickListener) {
            this.list = list;
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

        private void deleteImageFromStorage(String imageUrl) {
            try {
                StorageReference photoRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                photoRef.delete();
            } catch (IllegalArgumentException ignored) {
            }
        }

        private static int dpToPx(View v, int dp) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp,
                    v.getResources().getDisplayMetrics()
            );
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConsumedItem item = list.get(position);

            // Consumidos: ocultar acciones que no corresponden
            if (holder.btnArchivar != null) holder.btnArchivar.setVisibility(View.GONE);
            if (holder.editButton != null) holder.editButton.setVisibility(View.GONE);
            if (holder.iconOptimal != null)
                holder.iconOptimal.setVisibility(View.GONE); // no aplica

            // Imagen
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                PicassoClient.getInstance(holder.imageView.getContext())
                        .load(item.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .fit()
                        .centerCrop()
                        .into(holder.imageView);
            }

            holder.imageView.setOnClickListener(v -> {
                if (imageClickListener != null && item.imageUrl != null && !item.imageUrl.isEmpty()) {
                    imageClickListener.onImageClick(item.imageUrl);
                }
            });

            // Textos
            holder.nameTextView.setText(item.name);
            holder.varietyTextView.setText("Variedad: " + item.variety);
            holder.vintageTextView.setText("Año: " + item.vintage);
            holder.originTextView.setText("Origen: " + item.origin);
            holder.percentageTextView.setText("Alcohol: " + item.percentage);

            holder.categoryTextView.setText("Categoría: " + item.category);
            holder.commentTextView.setText("Comentario IA: " + item.comment);

            // ===============================================
            // ✅ CORRECCIÓN: Usar la función formatCLP
            // ===============================================
            holder.priceTextView.setText("Precio: " + ConsumedWinesActivity.formatCLP(item.price));

            holder.categoryTextView.setVisibility("No disponible".equals(item.category) ? View.GONE : View.VISIBLE);
            holder.commentTextView.setVisibility("Sin comentario del asistente".equals(item.comment) ? View.GONE : View.VISIBLE);

            // =========================================================
            // ✅ FECHA DE ARCHIVADO (Consumido) JUSTO ANTES DE ELIMINAR
            // =========================================================
            String archivedAtText = "Consumido: Sin fecha";

            // Opción 1: viene desde allFields (si lo estás usando)
            if (item.allFields != null) {
                Object raw = item.allFields.get("archivedAtText");
                if (raw != null) archivedAtText = raw.toString();
            }

            if (holder.archivedAtTextView != null) {
                holder.archivedAtTextView.setText(archivedAtText);
                holder.archivedAtTextView.setVisibility(View.VISIBLE);
            }
            // =========================================================

            // Botón eliminar full width (como lo querías)
            LinearLayout.LayoutParams delParams =
                    (LinearLayout.LayoutParams) holder.deleteButton.getLayoutParams();

            delParams.width = 0;
            delParams.weight = 1f;
            delParams.height = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 48,
                    holder.itemView.getResources().getDisplayMetrics()
            );
            holder.deleteButton.setLayoutParams(delParams);

            holder.deleteButton.setBackgroundResource(R.drawable.bg_full_red);
            holder.deleteButton.setText("Eliminar");
            holder.deleteButton.setTextColor(Color.WHITE);
            holder.deleteButton.setAllCaps(false);
            holder.deleteButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

            try {
                com.google.android.material.button.MaterialButton mb =
                        (com.google.android.material.button.MaterialButton) holder.deleteButton;
                mb.setInsetTop(0);
                mb.setInsetBottom(0);
            } catch (Exception ignored) {
            }

            // Eliminar
            holder.deleteButton.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return;

                ConsumedItem currentItem = list.get(currentPosition);

                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Eliminar vino")
                        .setMessage("¿Estás seguro de que deseas eliminar este vino?")
                        .setPositiveButton("Sí", (dialog, which) -> {

                            FirebaseFirestore.getInstance()
                                    .collection("descriptions")
                                    .document(userId)
                                    .collection("wineDescriptions")
                                    .document(currentItem.documentId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        if (currentItem.imageUrl != null && !currentItem.imageUrl.isEmpty()) {
                                            deleteImageFromStorage(currentItem.imageUrl);
                                        }

                                        list.remove(currentPosition);
                                        notifyItemRemoved(currentPosition);
                                        notifyItemRangeChanged(currentPosition, list.size());

                                        Toast.makeText(holder.itemView.getContext(),
                                                "Vino eliminado correctamente",
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(holder.itemView.getContext(),
                                                    "Error al eliminar vino", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView, iconOptimal;
            TextView archivedAtTextView;
            TextView nameTextView, varietyTextView, vintageTextView,
                    originTextView, percentageTextView,
                    categoryTextView, commentTextView, priceTextView;
            Button deleteButton, editButton;
            android.widget.ImageButton btnArchivar;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.itemImageView);
                iconOptimal = itemView.findViewById(R.id.iconOptimal);

                nameTextView = itemView.findViewById(R.id.itemName);
                varietyTextView = itemView.findViewById(R.id.itemVariety);
                vintageTextView = itemView.findViewById(R.id.itemVintage);
                originTextView = itemView.findViewById(R.id.itemOrigin);
                percentageTextView = itemView.findViewById(R.id.itemPercentage);

                categoryTextView = itemView.findViewById(R.id.itemCategory);
                commentTextView = itemView.findViewById(R.id.itemComment);
                priceTextView = itemView.findViewById(R.id.itemPrice);

                deleteButton = itemView.findViewById(R.id.buttonDelete);
                editButton = itemView.findViewById(R.id.buttonEdit);
                btnArchivar = itemView.findViewById(R.id.btnArchivar);

                // ===============================
                // ✅ FECHA "Consumido"
                // ===============================
                archivedAtTextView = new TextView(itemView.getContext());
                archivedAtTextView.setId(View.generateViewId()); // ✅ importante en ConstraintLayout
                archivedAtTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                archivedAtTextView.setTextColor(Color.parseColor("#888888"));
                archivedAtTextView.setText("Consumido: Sin fecha");

                // 👉 Parent del bloque de textos (donde están Precio/Comentario/etc.)
                ViewGroup textParent = (ViewGroup) priceTextView.getParent();

                // Evitar duplicados
                if (archivedAtTextView.getParent() != null) {
                    ((ViewGroup) archivedAtTextView.getParent()).removeView(archivedAtTextView);
                }

                // ✅ Si es ConstraintLayout, hay que poner constraints sí o sí
                if (textParent instanceof androidx.constraintlayout.widget.ConstraintLayout) {

                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp =
                            new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            );

                    // ⬇️ Bajo el comentario (si comentario está GONE, igual quedará bien)
                    clp.topToBottom = commentTextView.getId();

                    // ⬅️ Alineado al inicio del bloque de textos (mismo start que comment/price)
                    clp.startToStart = commentTextView.getId();

                    clp.topMargin = dpToPx(itemView, 6);
                    clp.bottomMargin = dpToPx(itemView, 6);

                    archivedAtTextView.setLayoutParams(clp);
                    textParent.addView(archivedAtTextView);

                } else {
                    // ✅ Si fuera LinearLayout u otro, funciona normal
                    ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    lp.topMargin = dpToPx(itemView, 6);
                    lp.bottomMargin = dpToPx(itemView, 6);
                    archivedAtTextView.setLayoutParams(lp);

                    // Lo agregamos al final del bloque de textos
                    textParent.addView(archivedAtTextView);
                }
            }
        }
    }

    public static String formatCLP(String rawPrice) {
        if (rawPrice == null || rawPrice.isEmpty() || rawPrice.equals("No disponible")) {
            return "No disponible";
        }

        try {
            // 1. Convertimos a String y quitamos espacios en blanco extremos
            String str = String.valueOf(rawPrice).trim();

            // 2. DETECCIÓN Y CORTE MANUAL DEL DECIMAL .0
            // Buscamos el último punto. Si lo que sigue es un 0, cortamos la cadena ahí.
            int lastDot = str.lastIndexOf('.');
            if (lastDot != -1) {
                // Verificamos si es un decimal .0 o .00 (y no un punto de mil como 1.500)
                String decimals = str.substring(lastDot);
                if (decimals.equals(".0") || decimals.equals(".00")) {
                    str = str.substring(0, lastDot); // Cortamos: "300.0" pasa a ser "300"
                }
            }

            // 3. Limpieza de símbolos (Dejamos solo números)
            // Esto convierte "$ 1.500" -> "1500" o "300" -> "300"
            String digitsOnly = str.replaceAll("[^0-9]", "");

            if (digitsOnly.isEmpty()) return rawPrice;

            // 4. Convertimos a número entero (Long)
            long val = Long.parseLong(digitsOnly);

            // 5. Formateamos a Peso Chileno
            java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "CL"));
            nf.setMaximumFractionDigits(0); // Forzamos CERO decimales

            return nf.format(val);

        } catch (Exception e) {
            // RED DE SEGURIDAD EXTREMA:
            // Si todo falla, devolvemos el texto original pero borramos el .0 manualmente
            return rawPrice.replace(".0", "").replace(".00", "");
        }
    }
}