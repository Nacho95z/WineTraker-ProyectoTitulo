package com.example.winertraker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.graphics.Color;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 112;

    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private String userId;

    // Componentes UI
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon;
    private TextView headerTitle, headerEmail;
    private TextView txtTotalWines, txtOptimalWines, txtTotalCellarValue;
    private CardView cardScan, cardCollection;
    private PieChart pieChart;
    private BarChart barChart;
    private LineChart valueLineChart;

    // SwipeRefresh
    private SwipeRefreshLayout swipeRefreshLayout;

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

        initializeViews();
        setupUserInfo();
        setupActions();
        createNotificationChannel();

        // SwipeRefresh
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#B22034")); // Rojo vino
        swipeRefreshLayout.setOnRefreshListener(this::loadCollectionStats);

        // Cargar datos por primera vez
        loadCollectionStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null) {
            loadCollectionStats();
        }
    }

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        // Header del drawer
        View headerView = navigationView.getHeaderView(0);
        headerTitle = headerView.findViewById(R.id.headerTitle);
        headerEmail = headerView.findViewById(R.id.headerEmail);

        // Dashboard
        txtTotalWines = findViewById(R.id.txtTotalWines);
        txtOptimalWines = findViewById(R.id.txtOptimalWines);
        txtTotalCellarValue = findViewById(R.id.txtTotalCellarValue);   // 👈 IMPORTANTE
        cardScan = findViewById(R.id.cardScan);
        cardCollection = findViewById(R.id.cardCollection);
        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        valueLineChart = findViewById(R.id.valueLineChart);             // 👈 IMPORTANTE

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
    }

    private void setupUserInfo() {
        if (user != null) {
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) {
                displayName = "Amante del vino";
            }
            headerTitle.setText(displayName);
            headerEmail.setText(user.getEmail());
        }
    }

    private void setupActions() {
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Toast.makeText(HomeActivity.this, "Inicio", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_my_cellar) {
                redirectToActivity(ViewCollectionActivity.class);
            } else if (id == R.id.nav_settings) {
                redirectToActivity(SettingsActivity.class);
            } else if (id == R.id.nav_logout) {
                performLogout();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        cardScan.setOnClickListener(v -> redirectToActivity(CaptureIMG.class));
        cardCollection.setOnClickListener(v -> redirectToActivity(ViewCollectionActivity.class));
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("remember_session", false)
                .apply();
        Intent intent = new Intent(HomeActivity.this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void loadCollectionStats() {
        if (userId == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            swipeRefreshLayout.setRefreshing(false);

            if (task.isSuccessful() && task.getResult() != null) {
                int totalWines = 0;
                int optimalCount = 0;
                Map<String, Integer> wineVarietyCounts = new HashMap<>();
                List<String> optimalWineNames = new ArrayList<>();

                int[] monthCounts = new int[12];       // botellas por mes
                double[] monthValues = new double[12]; // valor por mes
                double totalCellarValue = 0.0;         // valor total bodega

                for (QueryDocumentSnapshot document : task.getResult()) {
                    totalWines++;

                    String variety = document.getString("variety");
                    String vintageStr = document.getString("vintage");
                    String wineName = document.getString("wineName");

                    if (variety != null) {
                        variety = capitalize(variety);
                        wineVarietyCounts.put(
                                variety,
                                wineVarietyCounts.getOrDefault(variety, 0) + 1
                        );

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

                    // createdAt -> mes
                    Timestamp ts = document.getTimestamp("createdAt");
                    Integer monthIndex = null;
                    if (ts != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(ts.toDate());
                        monthIndex = cal.get(Calendar.MONTH); // 0-11
                        if (monthIndex >= 0 && monthIndex < 12) {
                            monthCounts[monthIndex]++;
                        }
                    }

                    // Precio
                    Double price = null;
                    Object priceObj = document.get("price");
                    if (priceObj instanceof Number) {
                        price = ((Number) priceObj).doubleValue();
                    } else if (priceObj instanceof String) {
                        try {
                            price = Double.parseDouble((String) priceObj);
                        } catch (NumberFormatException e) {
                            price = null;
                        }
                    }

                    if (price != null) {
                        totalCellarValue += price;
                        if (monthIndex != null && monthIndex >= 0 && monthIndex < 12) {
                            monthValues[monthIndex] += price;
                        }
                    }
                }

                txtTotalWines.setText(String.valueOf(totalWines));
                txtOptimalWines.setText(String.valueOf(optimalCount));
                updatePieChart(wineVarietyCounts);
                updateBarChart(monthCounts);
                updateValueLineChart(monthValues);
                updateTotalCellarValue(totalCellarValue);

                if (!optimalWineNames.isEmpty()) {
                    sendOptimalConsumptionNotification(optimalWineNames);
                }
            } else {
                txtTotalWines.setText("-");
                txtOptimalWines.setText("-");
            }
        }).addOnFailureListener(e -> {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "Error cargando datos", Toast.LENGTH_SHORT).show();
        });
    }

    // ----- Formateo y UI de valor -----

    private String formatCurrency(double value) {
        if (value <= 0) return "$0";
        DecimalFormat df = new DecimalFormat("$###,###");
        return df.format(value);
    }

    private void updateTotalCellarValue(double totalCellarValue) {
        if (txtTotalCellarValue == null) return;
        txtTotalCellarValue.setText("Valor total de la bodega: " + formatCurrency(totalCellarValue));
    }

    private void updateValueLineChart(double[] monthValues) {
        if (valueLineChart == null) return;

        final String[] months = {
                "Ene", "Feb", "Mar", "Abr",
                "May", "Jun", "Jul", "Ago",
                "Sep", "Oct", "Nov", "Dic"
        };

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (monthValues[i] > 0) {
                entries.add(new Entry(i, (float) monthValues[i]));
            }
        }

        if (entries.isEmpty()) {
            valueLineChart.clear();
            valueLineChart.setNoDataText("Sin información de valor para mostrar.");
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Valor mensual de la bodega");
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);

        // Curva suavizada premium
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        // Color de la línea
        int lineColor = Color.parseColor("#1E88E5"); // azul moderno
        dataSet.setColor(lineColor);
        dataSet.setCircleColor(lineColor);


        // Puntos
        dataSet.setCircleColor(lineColor);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setDrawCircleHole(true);

        // Etiquetas de valor en formato moneda
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPointLabel(Entry entry) {
                return formatCurrency(entry.getY());
            }
        });

        // Área rellena
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(this, R.drawable.area_gradient));

        // Sin líneas de highlight
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setDrawVerticalHighlightIndicator(false);

        // 🔴 AQUÍ ESTABA LO QUE FALTABA 🔴
        LineData lineData = new LineData(dataSet);
        valueLineChart.setData(lineData);
        // ---------------------------------

        // EJE X
        XAxis xAxis = valueLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(months));
        xAxis.setDrawGridLines(false);

        // EJE Y IZQUIERDO
        YAxis leftAxis = valueLineChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatCurrency(value);
            }
        });

        // Sin eje derecho
        valueLineChart.getAxisRight().setEnabled(false);

        // Estilo general
        valueLineChart.setDrawGridBackground(false);
        valueLineChart.setBackgroundColor(Color.WHITE);
        valueLineChart.getDescription().setEnabled(false);
        valueLineChart.getLegend().setEnabled(false);

        // Animación + refresco
        valueLineChart.animateX(900);
        valueLineChart.invalidate();
    }


    // ----- Pie chart variedades -----

    private void updatePieChart(Map<String, Integer> wineVarietyCounts) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), capitalize(entry.getKey())));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        pieChart.setData(data);
        pieChart.setUsePercentValues(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setCenterText("Variedades");
        pieChart.setCenterTextSize(16f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.animateXY(1400, 1400);
        pieChart.invalidate();
    }

    // ----- Bar chart botellas por mes -----

    private void updateBarChart(int[] monthCounts) {
        if (barChart == null) return;

        final String[] months = {
                "Ene", "Feb", "Mar", "Abr",
                "May", "Jun", "Jul", "Ago",
                "Sep", "Oct", "Nov", "Dic"
        };

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (monthCounts[i] > 0) {
                entries.add(new BarEntry(i, monthCounts[i]));
            }
        }

        if (entries.isEmpty()) {
            barChart.clear();
            barChart.setNoDataText("Sin registros suficientes para mostrar el historial.");
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Botellas registradas por mes");

        List<Integer> barColors = new ArrayList<>();
        barColors.add(Color.parseColor("#B22034")); // vino
        barColors.add(Color.parseColor("#FF9800")); // naranjo
        barColors.add(Color.parseColor("#FFEB3B")); // amarillo
        barColors.add(Color.parseColor("#4CAF50")); // verde
        barColors.add(Color.parseColor("#2196F3")); // azul
        barColors.add(Color.parseColor("#9C27B0")); // púrpura
        barColors.add(Color.parseColor("#009688")); // teal
        barColors.add(Color.parseColor("#E91E63")); // rosado
        barColors.add(Color.parseColor("#3F51B5")); // índigo
        barColors.add(Color.parseColor("#CDDC39")); // lima
        barColors.add(Color.parseColor("#FF5722")); // naranjo oscuro
        barColors.add(Color.parseColor("#795548")); // café

        dataSet.setColors(barColors);
        dataSet.setValueTextColor(Color.parseColor("#B22034"));
        dataSet.setValueTextSize(12f);

        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        barChart.setData(data);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(months));
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        barChart.getAxisRight().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setBackgroundColor(Color.WHITE);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);

        barChart.animateY(1000);
        barChart.invalidate();
    }

    // ----- Lógica de negocio + permisos + notificaciones -----

    private boolean isOptimalForConsumption(String variety, int vintageYear) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
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
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
