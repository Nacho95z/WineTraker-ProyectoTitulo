package com.example.winertraker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConsumedHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<QueryDocumentSnapshot> historyList = new ArrayList<>();
    private FirebaseFirestore db;
    private String userId;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consumed_history);

        // Ocultar Action Bar por defecto
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupDrawer();

        recyclerView = findViewById(R.id.recyclerHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        loadHistory();
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawerLayoutHistory);
        NavigationView nav = findViewById(R.id.navigationView);

        // Icono del menú para abrir el drawer
        findViewById(R.id.menuIcon).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        nav.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) startActivity(new Intent(this, HomeActivity.class));
            else if (id == R.id.nav_my_cellar) startActivity(new Intent(this, CaptureIMG.class));
            else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
            else if (id == R.id.nav_consumed_history) {
                // Ya estamos aquí, solo cerrar drawer
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, AuthActivity.class));
                finish();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void loadHistory() {
        db.collection("descriptions")
                .document(userId)
                .collection("consumedWines")
                .orderBy("consumedAt", Query.Direction.DESCENDING) // Ordenar por fecha consumo
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    historyList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        historyList.add(doc);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void deleteHistoryItem(String docId) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar registro")
                .setMessage("¿Borrar este vino de tu historial?")
                .setPositiveButton("Borrar", (d, w) -> {
                    db.collection("descriptions")
                            .document(userId)
                            .collection("consumedWines")
                            .document(docId)
                            .delete();
                    Toast.makeText(this, "Registro eliminado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // --- ADAPTER INTERNO ---
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        List<QueryDocumentSnapshot> list;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        public HistoryAdapter(List<QueryDocumentSnapshot> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_consumed, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            QueryDocumentSnapshot doc = list.get(position);

            holder.txtName.setText(doc.getString("wineName"));
            holder.txtVariety.setText(doc.getString("variety"));

            // Formatear Fecha
            Timestamp ts = doc.getTimestamp("consumedAt");
            if (ts != null) {
                holder.txtDate.setText("Consumido el: " + sdf.format(ts.toDate()));
            } else {
                holder.txtDate.setText("Consumido: Fecha desconocida");
            }

            String url = doc.getString("imageUrl");
            if (url != null && !url.isEmpty()) {
                Picasso.get().load(url).fit().centerCrop().into(holder.img);
            } else {
                holder.img.setImageResource(R.drawable.ic_wine); // Asegúrate de tener un drawable por defecto
            }

            holder.btnDelete.setOnClickListener(v -> deleteHistoryItem(doc.getId()));
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtVariety, txtDate;
            ImageView img, btnDelete;
            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtWineName);
                txtVariety = v.findViewById(R.id.txtVariety);
                txtDate = v.findViewById(R.id.txtConsumedDate);
                img = v.findViewById(R.id.imgWine);
                btnDelete = v.findViewById(R.id.btnDeleteHistory);
            }
        }
    }
}