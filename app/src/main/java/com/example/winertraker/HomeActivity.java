package com.example.winertraker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 112;

    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private String userId;

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon;

    // Header del menú lateral
    private TextView headerTitle, headerEmail;

    // Componentes del UI Dashboard
    private TextView txtTotalWines, txtOptimalWines;
    private CardView cardScan, cardCollection;
    private PieChart pieChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        checkAndRequestPermissions();

        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        if (user == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        userId = user.getUid();

        // Inicializar vistas
        initializeViews();

        // Configurar datos del usuario (en el header del menú)
        setupUserInfo();

        // Configurar botones de acción y menú
        setupActions();

        // Crear canal de notificaciones
        createNotificationChannel();

        // Cargar datos del dashboard (Gráficos y Contadores)
        loadCollectionStats();
    }

    private void initializeViews() {
        // Drawer
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        // Header del NavigationView
        View headerView = navigationView.getHeaderView(0);
        headerTitle = headerView.findViewById(R.id.headerTitle);
        headerEmail = headerView.findViewById(R.id.headerEmail);

        // Dashboard
        txtTotalWines = findViewById(R.id.txtTotalWines);
        txtOptimalWines = findViewById(R.id.txtOptimalWines);
        cardScan = findViewById(R.id.cardScan);
        cardCollection = findViewById(R.id.cardCollection);
        pieChart = findViewById(R.id.pieChart);
    }

    private void setupUserInfo() {
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "Amante del vino";
        }

        // Mostrar nombre y correo SOLO en el header del menú lateral
        headerTitle.setText(displayName);
        headerEmail.setText(user.getEmail());
    }

    private void setupActions() {

        // --- MENÚ LATERAL ---

        // Abrir el drawer al tocar el ícono de menú
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Manejar clics del menú
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int id = item.getItemId();

                        if (id == R.id.nav_home) {
                            // Ya estás en Home
                            Toast.makeText(HomeActivity.this, "Inicio", Toast.LENGTH_SHORT).show();

                        } else if (id == R.id.nav_my_cellar) {
                            // Ir a la bodega
                            redirectToActivity(ViewCollectionActivity.class);

                        } else if (id == R.id.nav_settings) {
                            // Aquí podrías abrir una Activity de Configuración
                            redirectToActivity(SettingsActivity.class);

                        } else if (id == R.id.nav_logout) {
                            // Cerrar sesión desde el menú
                            performLogout();
                        }

                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    }
                }
        );

        // Tarjeta Escanear
        cardScan.setOnClickListener(v -> redirectToActivity(CaptureIMG.class));

        // Tarjeta Ver Colección
        cardCollection.setOnClickListener(v -> redirectToActivity(ViewCollectionActivity.class));
    }

    private void performLogout() {
        // Cerrar sesión Firebase
        FirebaseAuth.getInstance().signOut();

        // Marcar que NO queremos recordar la sesión
        getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("remember_session", false)
                .apply();

        // Volver a pantalla de autenticación
        Intent intent = new Intent(HomeActivity.this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void loadCollectionStats() {
        if (userId == null) return;

        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int totalWines = 0;
                int optimalCount = 0;
                Map<String, Integer> wineVarietyCounts = new HashMap<>();
                List<String> optimalWineNames = new ArrayList<>();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    totalWines++;
                    String variety = document.getString("variety");
                    String vintageStr = document.getString("vintage");
                    String wineName = document.getString("wineName");

                    if (variety != null) {
                        variety = capitalize(variety);
                        wineVarietyCounts.put(variety, wineVarietyCounts.getOrDefault(variety, 0) + 1);

                        if (vintageStr != null) {
                            try {
                                int vintageYear = Integer.parseInt(vintageStr);
                                if (isOptimalForConsumption(variety, vintageYear)) {
                                    optimalCount++;
                                    if (wineName != null) {
                                        optimalWineNames.add(wineName);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // ACTUALIZAR DASHBOARD UI
                txtTotalWines.setText(String.valueOf(totalWines));
                txtOptimalWines.setText(String.valueOf(optimalCount));

                // Actualizar gráfico
                updatePieChart(wineVarietyCounts);

                // Notificar si es necesario
                if (!optimalWineNames.isEmpty()) {
                    sendOptimalConsumptionNotification(optimalWineNames);
                }
            } else {
                txtTotalWines.setText("-");
                txtOptimalWines.setText("-");
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error cargando datos", Toast.LENGTH_SHORT).show();
        });
    }

    // --- MÉTODOS AUXILIARES (Gráfico, Permisos, Lógica Vinos) ---

    private void updatePieChart(Map<String, Integer> wineVarietyCounts) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), capitalize(entry.getKey())));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        pieChart.setData(data);
        pieChart.setUsePercentValues(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(android.graphics.Color.TRANSPARENT);
        pieChart.setEntryLabelColor(android.graphics.Color.BLACK);
        pieChart.setCenterText("Variedades");
        pieChart.setCenterTextSize(16f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.animateXY(1400, 1400);
        pieChart.invalidate();
    }

    private boolean isOptimalForConsumption(String variety, int vintageYear) {
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
    }

    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "wine_optimal_channel";
            CharSequence name = "Consumo Óptimo";
            String description = "Notificación sobre consumo óptimo de vinos";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendOptimalConsumptionNotification(List<String> wineNames) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        if (wineNames.isEmpty()) return;

        Intent intent = new Intent(this, ViewCollectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "wine_optimal_channel")
                .setSmallIcon(R.drawable.ic_wine)
                .setContentTitle("¡Tienes vinos listos!")
                .setContentText("Tienes " + wineNames.size() + " botellas en su punto óptimo.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(1, builder.build());
    }

    private void redirectToActivity(Class<?> activityClass) {
        Intent intent = new Intent(HomeActivity.this, activityClass);
        startActivity(intent);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Implementación básica para cumplir con override
    }

    @Override
    public void onBackPressed() {
        // Si el drawer está abierto, ciérralo; si no, comportamiento normal
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}