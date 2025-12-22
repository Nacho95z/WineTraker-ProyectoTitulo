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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
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

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView; // ✅ zoom
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.text.Editable;
import android.text.TextWatcher;

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
    private View fullscreenOverlay;
    private PhotoView fullscreenImage;

    // Filtros
    private EditText editFilterValue;
    private Spinner spinnerField;
    private final List<String> filterFieldKeys = new ArrayList<>();
    private final Set<String> availableFilterKeys = new LinkedHashSet<>();
    private final List<CollectionItem> fullCollectionList = new ArrayList<>();

    // Óptimos
    private ArrayList<String> optimalWineIds;
    private boolean showOnlyOptimal = false;

    private SwitchMaterial switchOptimalOnly;
    private boolean filterOnlyOptimal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Firebase
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        userId = (user != null) ? user.getUid() : null;

        // Si venimos desde Home con filtro
        optimalWineIds = getIntent().getStringArrayListExtra("optimalWineIds");
        String filterMode = getIntent().getStringExtra("filterMode");

        showOnlyOptimal = filterMode != null
                && filterMode.equals("optimal")
                && optimalWineIds != null
                && !optimalWineIds.isEmpty();

        // Drawer + menú
        drawerLayoutCollection = findViewById(R.id.drawerLayoutCollection);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        menuIcon.setOnClickListener(v ->
                drawerLayoutCollection.openDrawer(GravityCompat.START)
        );

        // Header del drawer
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

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                Intent intent = new Intent(ViewCollectionActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_my_cellar) {
                drawerLayoutCollection.closeDrawer(GravityCompat.START);

            } else if (id == R.id.nav_consumed) {
                startActivity(new Intent(ViewCollectionActivity.this, ConsumedWinesActivity.class));

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
        switchOptimalOnly = findViewById(R.id.switchOptimalOnly);

        filterOnlyOptimal = showOnlyOptimal;
        switchOptimalOnly.setChecked(showOnlyOptimal);

        switchOptimalOnly.setOnCheckedChangeListener((button, isChecked) -> {
            filterOnlyOptimal = isChecked;
            String currentText = editFilterValue.getText() != null
                    ? editFilterValue.getText().toString().trim()
                    : "";
            applyFilter(currentText);
        });

        spinnerField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String currentText = editFilterValue.getText() != null
                        ? editFilterValue.getText().toString().trim()
                        : "";
                applyFilter(currentText);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                applyFilter("");
            }
        });

        editFilterValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        collectionList = new ArrayList<>();
        adapter = new CollectionAdapter(collectionList, userId, this::showFullImage);
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

        availableFilterKeys.clear();
        fullCollectionList.clear();
        collectionList.clear();

        userCollection.get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String documentId = document.getId();

                            Boolean archived = document.getBoolean("archived");
                            if (archived != null && archived) continue;

                            Map<String, Object> data = document.getData();
                            if (data == null) data = new HashMap<>();

                            String imageUrl = document.getString("imageUrl");
                            if (imageUrl == null || imageUrl.isEmpty()) {
                                Object rawUrl = data.get("imageUrl");
                                if (rawUrl != null) imageUrl = rawUrl.toString();
                            }

                            String name       = document.getString("wineName");
                            String variety    = document.getString("variety");
                            String vintage    = document.getString("vintage");
                            String origin     = document.getString("origin");
                            String percentage = document.getString("percentage");
                            String category   = document.getString("category");
                            String comment    = document.getString("comment");

                            // price puede venir como número o texto
                            String price = null;
                            Object rawPrice = document.get("price");
                            if (rawPrice != null) price = rawPrice.toString();

                            // ✅ Calcula si está en apogeo
                            boolean isOptimal = false;
                            try {
                                int vy = Integer.parseInt(vintageStrOr(vintage));
                                isOptimal = isInPeakNow(variety, category, vy);
                            } catch (Exception ignored) {}

                            CollectionItem item = new CollectionItem(
                                    documentId, imageUrl, name, variety, vintage,
                                    origin, percentage, category, comment, price,
                                    isOptimal, data
                            );

                            // ✅ SIEMPRE guarda TODO en la lista completa (para que el switch pueda mostrar “todas”)
                            fullCollectionList.add(item);

                            // ✅ Decide qué mostrar al inicio
                            if (showOnlyOptimal && optimalWineIds != null) {
                                if (optimalWineIds.contains(documentId)) {
                                    collectionList.add(item);
                                }
                            } else {
                                collectionList.add(item);
                            }

                            // Keys disponibles para filtros
                            for (String key : data.keySet()) {
                                if (shouldIncludeFieldForFilter(key)) {
                                    availableFilterKeys.add(key);
                                }
                            }
                        }

                        setupFilterSpinner();

                        String currentText = editFilterValue.getText() != null
                                ? editFilterValue.getText().toString().trim()
                                : "";
                        applyFilter(currentText);

                    } else {
                        Toast.makeText(this, "No se encontraron vinos en la colección", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error al cargar la colección", Toast.LENGTH_SHORT).show();
                });
    }

    private static boolean isInPeakNow(String variety, String category, int vintageYear) {
        PeakWindowCalculator.PeakWindow w =
                PeakWindowCalculator.calculate(variety, category, vintageYear);

        int now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        return now >= w.startYear && now <= w.endYear;
    }

    private static String vintageStrOr(String vintage) {
        if (vintage == null) return "";
        String s = vintage.trim();
        s = s.replaceAll("[^0-9]", "");
        if (s.length() > 4) s = s.substring(s.length() - 4);
        return s;
    }



    private boolean shouldIncludeFieldForFilter(String key) {
        if (key == null) return false;

        String k = key.trim().toLowerCase(Locale.ROOT);

        if (k.equals("rawtext") || k.equals("raw_text") || k.equals("rawtextfull")) return false;
        if (k.equals("comment")) return false;
        if (k.equals("imageurl") || k.equals("image_url")) return false;
        if (k.equals("createdat") || k.equals("updatedat")) return false;
        if (k.equals("recognizedtext")) return false;

        // ✅ apogeo no debería salir en filtros
        if (k.equals("peaktext") || k.equals("peakstartyear") || k.equals("peakendyear")) return false;

        return true;
    }

    private void setupFilterSpinner() {
        filterFieldKeys.clear();
        if (spinnerField == null) return;
        if (availableFilterKeys.isEmpty()) return;

        List<String> labels = new ArrayList<>();

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
            case "wineName": return "Nombre";
            case "variety": return "Variedad";
            case "vintage": return "Cosecha";
            case "origin": return "Origen / Región";
            case "percentage": return "Grado alcohólico";
            case "category": return "Categoría";
            case "price": return "Precio";
            default:
                return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
        }
    }

    private void applyFilter(String value) {
        value = (value != null) ? value.trim() : "";
        collectionList.clear();

        if (value.isEmpty()) {
            if (!filterOnlyOptimal) {
                collectionList.addAll(fullCollectionList);
            } else {
                for (CollectionItem item : fullCollectionList) {
                    if (item.isOptimal) collectionList.add(item);
                }
            }
            adapter.notifyDataSetChanged();
            return;
        }

        if (availableFilterKeys.isEmpty()) {
            collectionList.addAll(fullCollectionList);
            adapter.notifyDataSetChanged();
            return;
        }

        String valueLower = value.toLowerCase(Locale.ROOT);

        int pos = (spinnerField != null)
                ? spinnerField.getSelectedItemPosition()
                : Spinner.INVALID_POSITION;

        if (pos == Spinner.INVALID_POSITION || pos == 0 || filterFieldKeys.isEmpty()) {

            for (CollectionItem item : fullCollectionList) {
                if (item.allFields == null) continue;

                boolean matches = false;

                for (String key : availableFilterKeys) {
                    Object raw = item.allFields.get(key);
                    if (raw != null) {
                        String text = raw.toString().toLowerCase(Locale.ROOT);
                        if (text.contains(valueLower)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    if (filterOnlyOptimal && !item.isOptimal) continue;
                    collectionList.add(item);
                }
            }

        } else {
            String selectedKey = filterFieldKeys.get(pos);

            for (CollectionItem item : fullCollectionList) {
                if (item.allFields == null) continue;

                Object raw = item.allFields.get(selectedKey);
                if (raw == null) continue;

                String text = raw.toString();

                if ("percentage".equals(selectedKey) || "vintage".equals(selectedKey) || "price".equals(selectedKey)) {
                    String cleanField = text.replaceAll("[^0-9]", "");
                    String cleanFilter = value.replaceAll("[^0-9]", "");

                    if (!cleanFilter.isEmpty() && cleanField.equals(cleanFilter)) {
                        if (filterOnlyOptimal && !item.isOptimal) continue;
                        collectionList.add(item);
                    }
                } else {
                    String textLower = text.toLowerCase(Locale.ROOT);
                    if (textLower.contains(valueLower)) {
                        if (filterOnlyOptimal && !item.isOptimal) continue;
                        collectionList.add(item);
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    // 🔍 Mostrar imagen grande
    private void showFullImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()
                || fullscreenOverlay == null || fullscreenImage == null) {
            return;
        }

        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.setAlpha(0f);

        PicassoClient.getInstance(fullscreenImage.getContext())
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .fit()
                .centerInside()
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

    // ---------------- MODELO ----------------

    private static class CollectionItem {
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
        boolean isOptimal;

        // ✅ NUEVO (apogeo)
        String peakText;
        Long peakStartYear;
        Long peakEndYear;

        Map<String, Object> allFields;

        CollectionItem(String documentId, String imageUrl, String name,
                       String variety, String vintage, String origin,
                       String percentage, String category, String comment, String price,
                       boolean isOptimal,
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
            this.isOptimal = isOptimal;
            this.allFields = (allFields != null) ? allFields : new HashMap<>();

            // ✅ Leer apogeo desde allFields (Firestore)
            Object rawPeakText = this.allFields.get("peakText");
            this.peakText = (rawPeakText != null) ? rawPeakText.toString() : null;

            Object rawStart = this.allFields.get("peakStartYear");
            Object rawEnd = this.allFields.get("peakEndYear");

            this.peakStartYear = (rawStart instanceof Number) ? ((Number) rawStart).longValue() : null;
            this.peakEndYear = (rawEnd instanceof Number) ? ((Number) rawEnd).longValue() : null;
        }
    }

    // ---------------- ADAPTER ----------------

    private static class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {

        interface OnImageClickListener {
            void onImageClick(String imageUrl);
        }

        private final List<CollectionItem> collectionList;
        private final String userId;
        private final OnImageClickListener imageClickListener;

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

        private void deleteImageFromStorage(String imageUrl) {
            try {
                StorageReference photoRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                photoRef.delete()
                        .addOnSuccessListener(aVoid ->
                                android.util.Log.d("Storage", "Imagen eliminada del Storage")
                        )
                        .addOnFailureListener(e ->
                                android.util.Log.e("Storage", "Error al eliminar imagen", e)
                        );
            } catch (IllegalArgumentException e) {
                android.util.Log.e("Storage", "URL de imagen inválida: " + imageUrl, e);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CollectionItem item = collectionList.get(position);

            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);

            if (item.isOptimal) {
                holder.iconOptimal.setVisibility(View.VISIBLE);
                Glide.with(holder.iconOptimal.getContext())
                        .asGif()
                        .load(R.drawable.is_wine_optimal)
                        .into(holder.iconOptimal);
            } else {
                holder.iconOptimal.setVisibility(View.GONE);
            }

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

            holder.nameTextView.setText(item.name);
            holder.varietyTextView.setText("Variedad: " + item.variety);
            holder.vintageTextView.setText("Año: " + item.vintage);
            holder.originTextView.setText("Origen: " + item.origin);
            holder.percentageTextView.setText("Alcohol: " + item.percentage);

            holder.categoryTextView.setText("Categoría: " + item.category);
            holder.commentTextView.setText("Comentario IA: " + item.comment);
            holder.priceTextView.setText("Precio: " + formatCLP(item.price));

            // ✅ Apogeo: usar el guardado en Firestore; fallback si no existe (docs antiguos)
            String peak = item.peakText;

            if ((peak == null || peak.trim().isEmpty())
                    && item.variety != null
                    && item.vintage != null
                    && item.vintage.matches("\\d{4}")) {
                try {
                    int y = Integer.parseInt(item.vintage);
                    PeakWindowCalculator.PeakWindow pw =
                            PeakWindowCalculator.calculate(item.variety, item.category, y);
                    peak = pw.message;
                } catch (Exception ignored) { }
            }

            if (holder.peakTextView != null) {
                if (peak == null || peak.trim().isEmpty()) {
                    holder.peakTextView.setVisibility(View.GONE);
                } else {
                    holder.peakTextView.setText("Apogeo: " + peak);
                    holder.peakTextView.setVisibility(View.VISIBLE);
                }
            }

            if ("No disponible".equals(item.category)) {
                holder.categoryTextView.setVisibility(View.GONE);
            } else {
                holder.categoryTextView.setVisibility(View.VISIBLE);
            }

            if ("Sin comentario del asistente".equals(item.comment)) {
                holder.commentTextView.setVisibility(View.GONE);
            } else {
                holder.commentTextView.setVisibility(View.VISIBLE);
            }

            holder.deleteButton.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return;

                CollectionItem currentItem = collectionList.get(currentPosition);

                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Eliminar vino")
                        .setMessage("¿Estás seguro de que deseas eliminar este vino?")
                        .setPositiveButton("Sí", (dialog, which) -> {

                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                            firestore.collection("descriptions")
                                    .document(userId)
                                    .collection("wineDescriptions")
                                    .document(currentItem.documentId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {

                                        if (currentItem.imageUrl != null && !currentItem.imageUrl.isEmpty()) {
                                            deleteImageFromStorage(currentItem.imageUrl);
                                        }

                                        collectionList.remove(currentPosition);
                                        notifyItemRemoved(currentPosition);
                                        notifyItemRangeChanged(currentPosition, collectionList.size());

                                        Toast.makeText(holder.itemView.getContext(),
                                                "Vino eliminado correctamente",
                                                Toast.LENGTH_SHORT).show();
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

            holder.btnArchivar.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) return;

                CollectionItem currentItem = collectionList.get(currentPosition);

                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Archivar vino")
                        .setMessage("¿Mover este vino a \"Consumidos\"? (Ya no aparecerá en tu bodega)")
                        .setPositiveButton("Sí", (dialog, which) -> {

                            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                            firestore.collection("descriptions")
                                    .document(userId)
                                    .collection("wineDescriptions")
                                    .document(currentItem.documentId)
                                    .update(
                                            "archived", true,
                                            "archivedAt", FieldValue.serverTimestamp()
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        collectionList.remove(currentPosition);
                                        notifyItemRemoved(currentPosition);
                                        notifyItemRangeChanged(currentPosition, collectionList.size());

                                        Toast.makeText(holder.itemView.getContext(),
                                                "Archivado en Consumidos",
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(holder.itemView.getContext(),
                                                    "Error al archivar", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
        }

        private void showEditDialog(Context context, CollectionItem item, int position) {

            View dialogView = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_edit_item, null);

            TextInputEditText editName = dialogView.findViewById(R.id.editName);
            TextInputEditText editVariety = dialogView.findViewById(R.id.editVariety);
            TextInputEditText editVintage = dialogView.findViewById(R.id.editVintage);
            TextInputEditText editOrigin = dialogView.findViewById(R.id.editOrigin);
            TextInputEditText editPercentage = dialogView.findViewById(R.id.editPercentage);

            TextInputEditText editCategory = dialogView.findViewById(R.id.editCategory);
            TextInputEditText editPrice = dialogView.findViewById(R.id.editPrice);
            TextInputEditText editComment = dialogView.findViewById(R.id.editComment);

            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnSave = dialogView.findViewById(R.id.btnSave);

            editName.setText(item.name);
            editVariety.setText(item.variety);
            editVintage.setText(item.vintage);
            editOrigin.setText(item.origin);
            editPercentage.setText(item.percentage);

            editCategory.setText(item.category);
            editPrice.setText(priceForEdit(item.price));
            editComment.setText(item.comment);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT)
                );
            }

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnSave.setOnClickListener(v -> {
                item.name = getTextOrEmpty(editName);
                item.variety = getTextOrEmpty(editVariety);
                item.vintage = getTextOrEmpty(editVintage);
                item.origin = getTextOrEmpty(editOrigin);
                item.percentage = getTextOrEmpty(editPercentage);

                item.category = getTextOrEmpty(editCategory);
                item.price = getTextOrEmpty(editPrice);
                item.comment = getTextOrEmpty(editComment);

                String peakText = null;
                Long peakStart = null;
                Long peakEnd = null;

                // ✅ Recalcular apogeo + óptimo (1 sola vez)
                try {
                    String cleanVintage = vintageStrOr(item.vintage);
                    if (cleanVintage.matches("\\d{4}")) {
                        int y = Integer.parseInt(cleanVintage);

                        PeakWindowCalculator.PeakWindow pw =
                                PeakWindowCalculator.calculate(item.variety, item.category, y);

                        peakText = pw.message;
                        peakStart = (long) pw.startYear;
                        peakEnd = (long) pw.endYear;

                        item.isOptimal = isInPeakNow(item.variety, item.category, y);

                        item.peakText = peakText;
                        item.peakStartYear = peakStart;
                        item.peakEndYear = peakEnd;
                    } else {
                        item.isOptimal = false;
                    }
                } catch (Exception e) {
                    item.isOptimal = false;
                }

                // ✅ Mantener allFields sincronizado (para filtros)
                if (item.allFields == null) item.allFields = new HashMap<>();

                item.allFields.put("wineName", item.name);
                item.allFields.put("variety", item.variety);
                item.allFields.put("vintage", item.vintage);
                item.allFields.put("origin", item.origin);
                item.allFields.put("percentage", item.percentage);
                item.allFields.put("category", item.category);
                item.allFields.put("price", item.price);
                item.allFields.put("comment", item.comment);

                // ✅ apogeo también
                item.allFields.put("peakText", peakText);
                item.allFields.put("peakStartYear", peakStart);
                item.allFields.put("peakEndYear", peakEnd);

                // (Opcional) si filtras por archived, podrías mantenerlo también:
                // item.allFields.put("archived", false);


                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                firestore.collection("descriptions")
                        .document(userId)
                        .collection("wineDescriptions")
                        .document(item.documentId)
                        .update(
                                "wineName", item.name,
                                "variety", item.variety,
                                "vintage", item.vintage,
                                "origin", item.origin,
                                "percentage", item.percentage,
                                "category", item.category,
                                "price", item.price,
                                "comment", item.comment,

                                // ✅ apogeo
                                "peakText", peakText,
                                "peakStartYear", peakStart,
                                "peakEndYear", peakEnd
                        )
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show();

                            // ✅ Refresca según filtro/switch actual
                            if (context instanceof ViewCollectionActivity) {
                                ViewCollectionActivity act = (ViewCollectionActivity) context;
                                String currentText = act.editFilterValue.getText() != null
                                        ? act.editFilterValue.getText().toString().trim()
                                        : "";
                                act.applyFilter(currentText);
                            } else {
                                notifyItemChanged(position);
                            }

                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show()
                        );
            });


            dialog.show();
        }

        private String getTextOrEmpty(TextInputEditText editText) {
            return editText.getText() != null
                    ? editText.getText().toString().trim()
                    : "";
        }

        @Override
        public int getItemCount() {
            return collectionList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView, iconOptimal;
            TextView nameTextView, varietyTextView, vintageTextView,
                    originTextView, percentageTextView,
                    categoryTextView, commentTextView, priceTextView,
                    peakTextView; // ✅ NUEVO
            Button deleteButton, editButton;
            ImageButton btnArchivar;

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

                // ✅ Debe existir en item_collection.xml
                peakTextView = itemView.findViewById(R.id.itemPeak);

                deleteButton = itemView.findViewById(R.id.buttonDelete);
                editButton = itemView.findViewById(R.id.buttonEdit);
                btnArchivar = itemView.findViewById(R.id.btnArchivar);
            }
        }
    } // cierre adapter

    // ---------------- HELPERS ----------------

    private static String formatCLP(String rawPrice) {
        if (rawPrice == null || rawPrice.isEmpty()) return "No disponible";

        String str = rawPrice.trim()
                .replace("$", "")
                .replace("CLP", "")
                .replace(" ", "");

        if (str.endsWith(".0")) str = str.substring(0, str.length() - 2);
        if (str.endsWith(".00")) str = str.substring(0, str.length() - 3);

        str = str.replace(".", "")   // miles fuera
                .replace(",", ".");  // coma decimal -> punto

        try {
            double value = Double.parseDouble(str);
            java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
            nf.setMaximumFractionDigits(0);
            return nf.format(Math.round(value));
        } catch (NumberFormatException e) {
            return rawPrice;
        }
    }

    private static String priceForEdit(String rawPrice) {
        if (rawPrice == null || rawPrice.isEmpty() || rawPrice.equals("No disponible")) return "";

        String str = rawPrice.trim()
                .replace("$", "")
                .replace("CLP", "")
                .replace(" ", "");

        if (str.endsWith(".0")) str = str.substring(0, str.length() - 2);
        if (str.endsWith(".00")) str = str.substring(0, str.length() - 3);

        str = str.replace(".", ""); // ✅ miles fuera

        try {
            long value = Math.round(Double.parseDouble(str));
            return String.valueOf(value);
        } catch (NumberFormatException e) {
            return rawPrice.replace("$", "").replace("CLP", "").trim();
        }
    }
}
