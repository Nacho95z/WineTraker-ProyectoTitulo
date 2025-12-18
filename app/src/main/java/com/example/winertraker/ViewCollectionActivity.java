package com.example.winertraker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViewCollectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CollectionAdapter adapter;
    private List<QueryDocumentSnapshot> collectionList = new ArrayList<>();
    private List<QueryDocumentSnapshot> filteredList = new ArrayList<>();
    private FirebaseFirestore db;
    private String userId;

    // UI
    private SwitchMaterial switchOptimalOnly;
    private Spinner spinnerField;
    private EditText editFilterValue;
    private DrawerLayout drawerLayout;
    private View fullscreenOverlay;
    private PhotoView fullscreenImage;
    private String currentFilterField = "Todos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_collection);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        userId = user.getUid();

        initUI();
        setupDrawer();
        setupFilters();
        loadCollection();
    }

    private void initUI() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CollectionAdapter(filteredList, this);
        recyclerView.setAdapter(adapter);

        fullscreenOverlay = findViewById(R.id.fullscreenOverlay);
        fullscreenImage = findViewById(R.id.fullscreenImage);
        fullscreenOverlay.setOnClickListener(v -> toggleFullscreen(false));
        fullscreenImage.setOnClickListener(v -> toggleFullscreen(false));

        switchOptimalOnly = findViewById(R.id.switchOptimalOnly);
        spinnerField = findViewById(R.id.spinnerField);
        editFilterValue = findViewById(R.id.editFilterValue);
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawerLayoutCollection);
        findViewById(R.id.menuIcon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        NavigationView nav = findViewById(R.id.navigationView);
        nav.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) startActivity(new Intent(this, HomeActivity.class));
            else if (id == R.id.nav_my_cellar) startActivity(new Intent(this, CaptureIMG.class));
            else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
            else if (id == R.id.nav_consumed_history) {
                startActivity(new Intent(this, ConsumedHistoryActivity.class));
                //oast.makeText(this, "Historial (Próximamente)", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, AuthActivity.class));
                finish();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupFilters() {
        String[] options = {"Todos", "Nombre", "Variedad", "Año", "Origen", "Categoría"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, options);
        spinAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerField.setAdapter(spinAdapter);

        spinnerField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentFilterField = options[pos];
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        switchOptimalOnly.setOnCheckedChangeListener((b, c) -> applyFilters());
        editFilterValue.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                applyFilters(); return true;
            }
            return false;
        });
    }

    private void loadCollection() {
        db.collection("descriptions").document(userId).collection("wineDescriptions")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    collectionList.clear();
                    for (QueryDocumentSnapshot doc : value) collectionList.add(doc);
                    applyFilters();
                });
    }

    private void applyFilters() {
        filteredList.clear();
        boolean optimal = switchOptimalOnly.isChecked();
        String query = editFilterValue.getText().toString().toLowerCase().trim();

        for (QueryDocumentSnapshot doc : collectionList) {
            if (optimal && !isWineOptimal(doc)) continue;

            boolean match = query.isEmpty();
            if (!match) {
                if (currentFilterField.equals("Todos")) match = matchesAny(doc, query);
                else match = matchesField(doc, currentFilterField, query);
            }
            if (match) filteredList.add(doc);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matchesAny(QueryDocumentSnapshot doc, String q) {
        return safeContains(doc, "wineName", q) || safeContains(doc, "variety", q) ||
                safeContains(doc, "origin", q) || safeContains(doc, "vintage", q);
    }

    private boolean matchesField(QueryDocumentSnapshot doc, String field, String q) {
        String key = "wineName";
        if (field.equals("Variedad")) key = "variety";
        else if (field.equals("Año")) key = "vintage";
        else if (field.equals("Origen")) key = "origin";
        else if (field.equals("Categoría")) key = "category";
        return safeContains(doc, key, q);
    }

    private boolean safeContains(QueryDocumentSnapshot doc, String key, String q) {
        String val = doc.getString(key);
        return val != null && val.toLowerCase().contains(q);
    }

    private boolean isWineOptimal(QueryDocumentSnapshot doc) {
        String cat = doc.getString("category");
        String yr = doc.getString("vintage");
        if (cat == null || yr == null) return false;
        try {
            int age = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - Integer.parseInt(yr);
            if (cat.equalsIgnoreCase("Reserva")) return age >= 3 && age <= 5;
            if (cat.equalsIgnoreCase("Gran Reserva")) return age >= 4 && age <= 8;
        } catch (Exception ignored) {}
        return false;
    }

    // --- ACCIONES ---

    private void showEditDialog(QueryDocumentSnapshot doc) {
        AlertDialog dialog = new AlertDialog.Builder(this).setView(getLayoutInflater().inflate(R.layout.dialog_edit_item, null)).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        // Bind Views del Dialog
        TextInputEditText etName = dialog.findViewById(R.id.editName);
        TextInputEditText etVariety = dialog.findViewById(R.id.editVariety);
        TextInputEditText etPrice = dialog.findViewById(R.id.editPrice);
        // ... (bindear resto de campos si es necesario)

        etName.setText(doc.getString("wineName"));
        etVariety.setText(doc.getString("variety"));
        Double p = doc.getDouble("price");
        etPrice.setText(p != null ? String.valueOf(p) : "");

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSave).setOnClickListener(v -> {
            double priceVal = 0;
            try { priceVal = Double.parseDouble(etPrice.getText().toString()); } catch(Exception e){}

            db.collection("descriptions").document(userId).collection("wineDescriptions").document(doc.getId())
                    .update("wineName", etName.getText().toString(), "variety", etVariety.getText().toString(), "price", priceVal)
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });
    }

    private void deleteItem(QueryDocumentSnapshot doc) {
        new AlertDialog.Builder(this).setTitle("Eliminar").setMessage("¿Borrar definitivamente?")
                .setPositiveButton("Sí", (d, w) -> {
                    db.collection("descriptions").document(userId).collection("wineDescriptions").document(doc.getId()).delete();
                    String url = doc.getString("imageUrl");
                    if (url != null) FirebaseStorage.getInstance().getReferenceFromUrl(url).delete();
                }).setNegativeButton("No", null).show();
    }

    private void markAsConsumed(QueryDocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        data.put("consumedAt", Timestamp.now());

        db.collection("descriptions").document(userId).collection("consumedWines").add(data)
                .addOnSuccessListener(r -> {
                    db.collection("descriptions").document(userId).collection("wineDescriptions").document(doc.getId()).delete();
                    Toast.makeText(this, "¡Salud! Movido al historial.", Toast.LENGTH_SHORT).show();
                });
    }

    private void toggleFullscreen(boolean show) {
        if (show) {
            fullscreenOverlay.setVisibility(View.VISIBLE);
            fullscreenOverlay.animate().alpha(1f).setDuration(200);
        } else {
            fullscreenOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> fullscreenOverlay.setVisibility(View.GONE));
        }
    }

    // --- ADAPTER ---
    private class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.VH> {
        List<QueryDocumentSnapshot> list;
        Context ctx;

        public CollectionAdapter(List<QueryDocumentSnapshot> l, Context c) { list = l; ctx = c; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_collection, p, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            QueryDocumentSnapshot doc = list.get(pos);
            h.name.setText(doc.getString("wineName"));
            h.variety.setText("Variedad: " + doc.getString("variety"));
            h.year.setText("Año: " + doc.getString("vintage"));

            String url = doc.getString("imageUrl");
            if (url != null && !url.isEmpty()) {
                Picasso.get().load(url).fit().centerCrop().into(h.img);
                h.img.setOnClickListener(v -> {
                    Picasso.get().load(url).into(fullscreenImage);
                    toggleFullscreen(true);
                });
            } else h.img.setImageResource(R.drawable.ic_wine);

            h.iconOpt.setVisibility(isWineOptimal(doc) ? View.VISIBLE : View.GONE);

            h.btnDel.setOnClickListener(v -> deleteItem(doc));
            h.btnEdit.setOnClickListener(v -> showEditDialog(doc));
            h.btnConsume.setOnClickListener(v ->
                    new AlertDialog.Builder(ctx).setTitle("Confirmar consumo")
                            .setMessage("¿Disfrutaste este vino?")
                            .setPositiveButton("¡Sí!", (d, w) -> markAsConsumed(doc))
                            .setNegativeButton("Cancelar", null).show()
            );
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, variety, year;
            ImageView img, iconOpt;
            Button btnDel, btnEdit, btnConsume;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                variety = v.findViewById(R.id.itemVariety);
                year = v.findViewById(R.id.itemVintage);
                img = v.findViewById(R.id.itemImageView);
                iconOpt = v.findViewById(R.id.iconOptimal);
                btnDel = v.findViewById(R.id.buttonDelete);
                btnEdit = v.findViewById(R.id.buttonEdit);
                btnConsume = v.findViewById(R.id.buttonConsumed);
            }
        }
    }
}