package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.Timestamp;

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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;
import android.widget.AdapterView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.bumptech.glide.Glide;

// Filtros dinámicos
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

public class ConsumedWinesActivity extends AppCompatActivity {

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

    // Filtros dinámicos
    private EditText editFilterValue;

    private final List<CollectionItem> fullCollectionList = new ArrayList<>(); // lista completa sin filtrar
    private final Set<String> availableFilterKeys = new LinkedHashSet<>();     // set para construir dinámicamente
    // Filtros dinámicos
    private Spinner spinnerField;
    private final List<String> filterFieldKeys = new ArrayList<>();            // keys reales (wineName, variety, etc.)
    private ArrayList<String> optimalWineIds;  // 👈 IDs que vienen desde Home
    private boolean showOnlyOptimal = false;   // 👈 Modo "listos para beber"

    // 👇 NUEVO TOGGLE
    private SwitchMaterial switchOptimalOnly;
    private boolean filterOnlyOptimal = false;   // estado actual del toggle


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);

        // Cambiar título del header
        TextView title = findViewById(R.id.titleText);
        title.setText("Consumidos");

        // Ocultar "Botellas en su Apogeo" (texto + switch)
        View optimalRow = findViewById(R.id.optimalToggleRow);
        optimalRow.setVisibility(View.GONE);

        // Ocultar ActionBar para usar nuestro header custom
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Firebase
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();
        userId = (user != null) ? user.getUid() : null;

        // 👇 Revisar si venimos desde la Uva / Home con filtro de óptimos
        optimalWineIds = getIntent().getStringArrayListExtra("optimalWineIds");
        String filterMode = getIntent().getStringExtra("filterMode");

        showOnlyOptimal = filterMode != null
                && filterMode.equals("optimal")
                && optimalWineIds != null
                && !optimalWineIds.isEmpty();

        // (Opcional) podrías cambiar el título de la pantalla si estás en modo filtro
        if (showOnlyOptimal) {
            // Si tienes un TextView de título custom en el layout, podrías hacer:
            // TextView tvTitle = findViewById(R.id.tvTitle);
            // tvTitle.setText("Botellas listas para beber");
        }


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
                Intent intent = new Intent(ConsumedWinesActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_my_cellar) {
                // ✅ Volver a la bodega
                Intent intent = new Intent(ConsumedWinesActivity.this, ViewCollectionActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_consumed) {
                // ✅ Ya estás en Consumidos: solo cerrar el drawer (no abrir otra instancia)
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


        // 🔎 Barra de filtros

        spinnerField = findViewById(R.id.spinnerField);
        editFilterValue = findViewById(R.id.editFilterValue);
        switchOptimalOnly = findViewById(R.id.switchOptimalOnly);

        // El toggle arranca según desde dónde vengas:
        filterOnlyOptimal = showOnlyOptimal;
        switchOptimalOnly.setChecked(showOnlyOptimal);

        switchOptimalOnly.setOnCheckedChangeListener((button, isChecked) -> {
            filterOnlyOptimal = isChecked;
            String currentText = editFilterValue.getText() != null
                    ? editFilterValue.getText().toString().trim()
                    : "";
            applyFilter(currentText);
        });



        // Cuando el usuario cambia el campo en el spinner, volvemos a aplicar el filtro
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
                // Si no hay nada seleccionado, mostramos todo
                applyFilter("");
            }
        });


        // Cuando el usuario escribe, aplicamos filtro al vuelo
        editFilterValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });




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

    private void setHeaderTitleIfExists(String newTitle) {
        // intenta varios nombres comunes de id (ajusta si sabes el real)
        String[] possibleIds = {"textViewTitle", "tvTitle", "titleTextView", "txtTitle", "title"};

        for (String name : possibleIds) {
            int id = getResources().getIdentifier(name, "id", getPackageName());
            if (id != 0) {
                android.view.View v = findViewById(id);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setText(newTitle);
                    return;
                }
            }
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

                            // ✅ SOLO mostrar vinos archivados (consumidos)
                            Boolean archived = document.getBoolean("archived");
                            if (archived == null || !archived) {
                                continue;
                            }

                            // 👇 Usamos getData SOLO para filtros (allFields)
                            Map<String, Object> data = document.getData();
                            if (data == null) data = new HashMap<>();

                            // ✅ Fecha de archivado (para mostrar arriba)
                            com.google.firebase.Timestamp archivedTs = document.getTimestamp("archivedAt");
                            String archivedAtText = "Consumido: Sin fecha";
                            if (archivedTs != null) {
                                java.text.SimpleDateFormat sdf =
                                        new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault());
                                archivedAtText = "Consumido: " + sdf.format(archivedTs.toDate());
                            }
                            data.put("archivedAtText", archivedAtText);

                            // Image URL (fallback a data si viene raro)
                            String imageUrl = document.getString("imageUrl");
                            if (imageUrl == null || imageUrl.isEmpty()) {
                                Object rawUrl = data.get("imageUrl");
                                if (rawUrl != null) imageUrl = rawUrl.toString();
                            }

                            android.util.Log.d("ConsumedWines", "Doc " + documentId + " imageUrl = " + imageUrl);

                            // Campos principales
                            String name       = document.getString("wineName");
                            String variety    = document.getString("variety");
                            String vintage    = document.getString("vintage");
                            String origin     = document.getString("origin");
                            String percentage = document.getString("percentage");
                            String category   = document.getString("category");
                            String comment    = document.getString("comment");

                            // PRICE: puede ser número o texto
                            String price = null;
                            Object rawPrice = document.get("price");
                            if (rawPrice != null) price = rawPrice.toString();

                            // En Consumidos no tiene sentido "óptimo"
                            boolean isOptimal = false;

                            CollectionItem item = new CollectionItem(
                                    documentId, imageUrl, name, variety, vintage,
                                    origin, percentage, category, comment, price,
                                    isOptimal, data
                            );

                            fullCollectionList.add(item);
                            collectionList.add(item);

                            // Detectar campos filtrables dinámicamente
                            for (String key : data.keySet()) {
                                if (shouldIncludeFieldForFilter(key)) {
                                    availableFilterKeys.add(key);
                                }
                            }
                        }

                        // ⬅️ IMPORTANTE: llenar el Spinner una vez detectados los campos
                        setupFilterSpinner();

                        // Aplicar filtro actual (texto)
                        String currentText = editFilterValue.getText() != null
                                ? editFilterValue.getText().toString().trim()
                                : "";
                        applyFilter(currentText);

                    } else {
                        Toast.makeText(this, "No se encontraron vinos consumidos", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error al cargar consumidos", Toast.LENGTH_SHORT).show();
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

    // Omitimos campos que no queremos usar como filtro (robusto a case)
    private boolean shouldIncludeFieldForFilter(String key) {
        if (key == null) return false;

        String k = key.trim().toLowerCase();

        // Excluidos (agrega aquí todo lo "técnico" que no quieres mostrar)
        if (k.equals("rawtext") || k.equals("raw_text") || k.equals("rawtextfull")) return false;
        if (k.equals("comment")) return false;
        if (k.equals("imageurl") || k.equals("image_url")) return false;
        if (k.equals("createdat") || k.equals("updatedat")) return false; // 👈 esto saca createdAt
        if (k.equals("recognizedtext")) return false;


        return true;
    }



    private void setupFilterSpinner() {
        filterFieldKeys.clear();

        if (spinnerField == null) return;
        if (availableFilterKeys.isEmpty()) return;

        List<String> labels = new ArrayList<>();

        // Posición 0 = "Todos"
        filterFieldKeys.add(null);       // sin key asociada
        labels.add("Todos");

        // Luego, cada campo real
        for (String key : availableFilterKeys) {
            filterFieldKeys.add(key);
            labels.add(getDisplayNameForField(key));
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,   // tu layout personalizado
                labels
        );

        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerField.setAdapter(spinnerAdapter);

    }


    // Nombre amigable para el spinner
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
                // Capitaliza primera letra por defecto (para campos nuevos)
                return key.substring(0,1).toUpperCase() + key.substring(1);
        }
    }



    private void applyFilter(String value) {
        value = (value != null) ? value.trim() : "";
        collectionList.clear();

        // Si no hay texto, mostramos todo o solo óptimos según el toggle
        if (value.isEmpty()) {
            if (!filterOnlyOptimal) {
                collectionList.addAll(fullCollectionList);
            } else {
                for (CollectionItem item : fullCollectionList) {
                    if (item.isOptimal) {
                        collectionList.add(item);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            return;
        }


        if (availableFilterKeys.isEmpty()) {
            // Si por algún motivo no tenemos campos filtrables, mostramos todo
            collectionList.addAll(fullCollectionList);
            adapter.notifyDataSetChanged();
            return;
        }

        String valueLower = value.toLowerCase();

        int pos = (spinnerField != null)
                ? spinnerField.getSelectedItemPosition()
                : Spinner.INVALID_POSITION;

        // 🟣 Caso 1: "Todos" o selección inválida → búsqueda global en todos los campos
        if (pos == Spinner.INVALID_POSITION || pos == 0 || filterFieldKeys.isEmpty()) {

            for (CollectionItem item : fullCollectionList) {
                if (item.allFields == null) continue;

                boolean matches = false;

                for (String key : availableFilterKeys) {
                    Object raw = item.allFields.get(key);
                    if (raw != null) {
                        String text = raw.toString().toLowerCase();
                        if (text.contains(valueLower)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    // 👇 si el toggle está activo, solo agregamos vinos óptimos
                    if (filterOnlyOptimal && !item.isOptimal) {
                        continue;
                    }
                    collectionList.add(item);
                }

            }
        } else {
            // 🟢 Caso 2: un campo específico elegido en el spinner
            String selectedKey = filterFieldKeys.get(pos); // posición 0 es "Todos", así que aquí siempre hay key

            for (CollectionItem item : fullCollectionList) {
                if (item.allFields == null) continue;

                Object raw = item.allFields.get(selectedKey);
                if (raw == null) continue;

                String text = raw.toString();

                if ("percentage".equals(selectedKey) || "vintage".equals(selectedKey) || "price".equals(selectedKey)) {
                    String cleanField = text.replaceAll("[^0-9]", "");
                    String cleanFilter = value.replaceAll("[^0-9]", "");

                    if (!cleanFilter.isEmpty() && cleanField.equals(cleanFilter)) {
                        if (filterOnlyOptimal && !item.isOptimal) {
                            continue;
                        }
                        collectionList.add(item);
                    }
                } else {
                    String textLower = text.toLowerCase();
                    if (textLower.contains(valueLower)) {
                        if (filterOnlyOptimal && !item.isOptimal) {
                            continue;
                        }
                        collectionList.add(item);
                    }
                }

            }
        }


        adapter.notifyDataSetChanged();
    }




    // 🔍 Mostrar imagen en grande con fade-in
    private void showFullImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()
                || fullscreenOverlay == null || fullscreenImage == null) {
            return;
        }

        fullscreenOverlay.setVisibility(View.VISIBLE);
        fullscreenOverlay.setAlpha(0f);

        // Carga la imagen en el PhotoView a pantalla completa
        PicassoClient.getInstance(fullscreenImage.getContext())
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .fit()
                .centerInside()   // o centerCrop, como prefieras
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
        String category;   // ✅ nuevo
        String comment;    // ✅ nuevo
        String price;
        boolean isOptimal;

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
            this.comment  = (comment  != null) ? comment  : "Sin comentario del asistente";
            this.price    = (price    != null) ? price    : "No disponible";
            this.isOptimal = isOptimal;
            this.allFields = (allFields != null) ? allFields : new HashMap<>();
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

            // ✅ En Consumidos: ocultar acciones que no corresponden
            holder.btnArchivar.setVisibility(View.GONE);
            holder.editButton.setVisibility(View.GONE); // opcional recomendado
            // holder.deleteButton queda visible (historial) o lo ocultas si quieres

            // Siempre reseteamos la imagen con un placeholder básico
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);

            if (item.isOptimal) {
                holder.iconOptimal.setVisibility(View.VISIBLE);

                Glide.with(holder.iconOptimal.getContext())
                        .asGif()
                        .load(R.drawable.is_wine_optimal) // 👈 mismo nombre del paso 1
                        .into(holder.iconOptimal);

            } else {
                holder.iconOptimal.setVisibility(View.GONE);
            }



            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                PicassoClient.getInstance(holder.imageView.getContext())
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

            // ✅ nuevos
            holder.categoryTextView.setText("Categoría: " + item.category);
            holder.commentTextView.setText("Comentario IA: " + item.comment);
            holder.priceTextView.setText("Precio: " + item.price);

            // Si prefieres ocultar cuando no haya info:
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

        // 1) Ocultar Archivar
            holder.btnArchivar.setVisibility(View.GONE);

        // 2) Hacer que "Eliminar" ocupe todo el espacio del grupo y quede centrado
        // Si deleteButton está dentro de un LinearLayout con weight, lo forzamos:
            LinearLayout.LayoutParams delParams =
                    (LinearLayout.LayoutParams) holder.deleteButton.getLayoutParams();

            delParams.width = 0;
            delParams.weight = 1f;
            delParams.height = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 48,
                    holder.itemView.getResources().getDisplayMetrics()
            );

            holder.deleteButton.setLayoutParams(delParams);

        // 3) Fondo redondeado completo (no split)
            holder.deleteButton.setBackgroundResource(R.drawable.bg_full_red);
            holder.deleteButton.setText("Eliminar");
            holder.deleteButton.setTextColor(Color.WHITE);
            holder.deleteButton.setAllCaps(false);
            holder.deleteButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        // 4) Quitar “insets” material si aplica
            try {
                com.google.android.material.button.MaterialButton mb =
                        (com.google.android.material.button.MaterialButton) holder.deleteButton;
                mb.setInsetTop(0);
                mb.setInsetBottom(0);
            } catch (Exception ignored) {}


            // --- Eliminar ---
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

            // --- Editar ---
            holder.editButton.setOnClickListener(v ->
                    showEditDialog(holder.itemView.getContext(), item, position)
            );


            // --- Archivar (Consumido) ---
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
                                        // sacar de la lista actual (bodega)
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

            TextInputEditText editName      = dialogView.findViewById(R.id.editName);
            TextInputEditText editVariety   = dialogView.findViewById(R.id.editVariety);
            TextInputEditText editVintage   = dialogView.findViewById(R.id.editVintage);
            TextInputEditText editOrigin    = dialogView.findViewById(R.id.editOrigin);
            TextInputEditText editPercentage= dialogView.findViewById(R.id.editPercentage);

            // 🔴 NUEVOS
            TextInputEditText editCategory  = dialogView.findViewById(R.id.editCategory);
            TextInputEditText editPrice     = dialogView.findViewById(R.id.editPrice);
            TextInputEditText editComment   = dialogView.findViewById(R.id.editComment);

            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnSave   = dialogView.findViewById(R.id.btnSave);

            // Rellenar con los datos actuales
            editName.setText(item.name);
            editVariety.setText(item.variety);
            editVintage.setText(item.vintage);
            editOrigin.setText(item.origin);
            editPercentage.setText(item.percentage);

            editCategory.setText(item.category);
            editPrice.setText(item.price);
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

                // 🔴 NUEVOS
                item.category = getTextOrEmpty(editCategory);
                item.price    = getTextOrEmpty(editPrice);
                item.comment  = getTextOrEmpty(editComment);

                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                firestore.collection("descriptions")
                        .document(userId)
                        .collection("wineDescriptions")
                        .document(item.documentId)
                        .update(
                                "wineName",   item.name,
                                "variety",    item.variety,
                                "vintage",    item.vintage,
                                "origin",     item.origin,
                                "percentage", item.percentage,
                                "category",   item.category,
                                "price",      item.price,
                                "comment",    item.comment
                        )
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show();
                            notifyItemChanged(position);
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show()
                        );
            });

            dialog.show();
        }

        // helper pequeño
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
            ImageView imageView, iconOptimal;   // 👈 NUEVO
            TextView nameTextView, varietyTextView, vintageTextView,
                    originTextView, percentageTextView,
                    categoryTextView, commentTextView, priceTextView;
            Button deleteButton, editButton;
            ImageButton btnArchivar;

            ViewHolder(View itemView) {
                super(itemView);
                imageView     = itemView.findViewById(R.id.itemImageView);
                iconOptimal   = itemView.findViewById(R.id.iconOptimal);   // 👈 NUEVO
                nameTextView  = itemView.findViewById(R.id.itemName);
                varietyTextView = itemView.findViewById(R.id.itemVariety);
                vintageTextView = itemView.findViewById(R.id.itemVintage);
                originTextView  = itemView.findViewById(R.id.itemOrigin);
                percentageTextView = itemView.findViewById(R.id.itemPercentage);
                categoryTextView   = itemView.findViewById(R.id.itemCategory);
                commentTextView    = itemView.findViewById(R.id.itemComment);
                priceTextView      = itemView.findViewById(R.id.itemPrice);
                deleteButton       = itemView.findViewById(R.id.buttonDelete);
                editButton         = itemView.findViewById(R.id.buttonEdit);
                btnArchivar = itemView.findViewById(R.id.btnArchivar);
            }
        }



    }
}