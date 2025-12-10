package com.example.winertraker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
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
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.graphics.drawable.Drawable;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import androidx.appcompat.app.AlertDialog;




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
    private TextView bannerTextView;
    private ValueAnimator bannerAnimator;
    private TextView tvOptimalBadge;
    // Lista actual de vinos en consumo óptimo
    private final List<String> currentOptimalWineNames = new ArrayList<>();



    // 🍇 GIF de uva
    private GifImageView headerGif;

    // Estado de consumo óptimo
    private boolean hasOptimalWines = false;


    // Rotación de mensajes del banner
    private final List<String> bannerMessages = new ArrayList<>();
    private int currentBannerIndex = 0;
    private static final long BANNER_CYCLE_MS = 15000;
    private final android.os.Handler bannerHandler = new android.os.Handler(android.os.Looper.getMainLooper());

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

        // 🆕 NUEVO: Verificar términos y condiciones al iniciar
        checkTermsAndConditions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null) {
            loadCollectionStats();
        }
    }

    // -------------------------------------------------------------
    // ⚖️ LÓGICA DE TÉRMINOS Y CONDICIONES (NUEVO)
    // -------------------------------------------------------------

    private void checkTermsAndConditions() {
        SharedPreferences prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);
        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);

        if (!termsAccepted) {
            showTermsDialog();
        }
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_termconditions); // Asegúrate de haber creado este layout
        dialog.setCancelable(false); // Evita cerrar tocando fuera o atrás

        // Configurar fondo transparente para ver bordes redondeados
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        CheckBox checkAccept = dialog.findViewById(R.id.checkAccept);
        Button btnAccept = dialog.findViewById(R.id.btnAcceptTerms);

        // Botón deshabilitado por defecto
        btnAccept.setEnabled(false);
        btnAccept.setAlpha(0.5f);

        // Activar botón solo si el checkbox está marcado
        checkAccept.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnAccept.setEnabled(isChecked);
            btnAccept.setAlpha(isChecked ? 1.0f : 0.5f);
        });

        btnAccept.setOnClickListener(v -> {
            // Guardar aceptación
            getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("terms_accepted", true)
                    .apply();

            dialog.dismiss();
            Toast.makeText(HomeActivity.this, "¡Bienvenido a WineTrack!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    // -------------------------------------------------------------
    // FIN LÓGICA TÉRMINOS Y CONDICIONES
    // -------------------------------------------------------------


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
        txtTotalCellarValue = findViewById(R.id.txtTotalCellarValue);
        cardScan = findViewById(R.id.cardScan);
        cardCollection = findViewById(R.id.cardCollection);
        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        valueLineChart = findViewById(R.id.valueLineChart);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        bannerTextView = findViewById(R.id.bannerTextView);

        // 🍇 Uva del header
        headerGif = findViewById(R.id.headerGifLarge); // o Rgif de la uva
        tvOptimalBadge = findViewById(R.id.tvOptimalBadge);

    }

    private void startBannerAnimationSingleCycle() {
        if (bannerTextView == null) return;

        bannerTextView.post(() -> {
            View parent = (View) bannerTextView.getParent();
            if (parent == null) return;

            float parentWidth = parent.getWidth();
            float textWidth = bannerTextView.getWidth();

            if (parentWidth == 0 || textWidth == 0) return;

            // Cancelar animación previa
            if (bannerAnimator != null) {
                bannerAnimator.cancel();
            }

            // El texto parte justo fuera del borde derecho
            bannerTextView.setTranslationX(parentWidth);

            bannerAnimator = ValueAnimator.ofFloat(parentWidth, -textWidth);
            bannerAnimator.setDuration(BANNER_CYCLE_MS);
            bannerAnimator.setInterpolator(new LinearInterpolator());
            bannerAnimator.setRepeatCount(0); // SOLO un ciclo

            bannerAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                bannerTextView.setTranslationX(value);
            });

            bannerAnimator.start();
        });
    }

    private void startBannerRotation() {
        if (bannerTextView == null || bannerMessages.isEmpty()) return;

        // Limpiar cualquier ciclo anterior
        bannerHandler.removeCallbacksAndMessages(null);
        currentBannerIndex = 0;

        Runnable rotationRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerMessages.isEmpty() || bannerTextView == null) return;

                String msg = bannerMessages.get(currentBannerIndex);
                currentBannerIndex = (currentBannerIndex + 1) % bannerMessages.size();

                bannerTextView.setText(msg);
                startBannerAnimationSingleCycle();

                // programar siguiente mensaje
                bannerHandler.postDelayed(this, BANNER_CYCLE_MS);
            }
        };

        // Lanzar de inmediato el primer mensaje
        bannerHandler.post(rotationRunnable);
    }


    private void updateBannerMessage(int totalWines, double totalCellarValue) {
        bannerMessages.clear();

        // Mensaje principal dinámico según la bodega
        if (totalWines >= 2) {
            String formattedValue = formatCurrency(totalCellarValue);
            bannerMessages.add(
                    "Tu bodega tiene " + totalWines + " botellas registradas • Valor estimado: " + formattedValue + "."
            );
        } else if (totalWines == 1) {
            String formattedValue = formatCurrency(totalCellarValue);
            bannerMessages.add(
                    "Tu bodega tiene " + totalWines + " botella registrada • Valor estimado: " + formattedValue + "."
            );
        } else {
            bannerMessages.add("WineTrack • Comienza registrando tus primeras botellas y organiza tu bodega digital.");
        }

        // Mensajes estáticos / tips enológicos
        bannerMessages.add("Dato SAG 2025: la producción total de vinos fue de 838,6 millones de litros, cerca de un 10% menos que el 2024.");
        bannerMessages.add("En 2025, el 82,5% del vino producido en Chile fue con denominación de origen, según SAG.");
        bannerMessages.add("Solo el 1,2% del vino producido en Chile el 2025 provino de uvas de mesa, de acuerdo al SAG.");
        bannerMessages.add("Maule concentra cerca del 47,4% de todo el vino producido en Chile en 2025, liderando a nivel nacional.");
        bannerMessages.add("Las regiones de Maule, O’Higgins y Coquimbo suman el 88,5% de la producción de vino chileno 2025.");
        bannerMessages.add("En 2025, el Cabernet Sauvignon representó alrededor del 28% de los vinos con denominación de origen en Chile.");
        bannerMessages.add("Sauvignon Blanc y Chardonnay suman más de un 27% de los vinos con D.O. producidos en Chile el 2025.");
        bannerMessages.add("Entre 2024 y 2025, los vinos con D.O. bajaron un 14%, pero los vinos sin D.O. crecieron un 17,1%, según el SAG.");
        bannerMessages.add("La producción de vinos para pisco creció cerca de un 18% en 2025 respecto de 2024, de acuerdo al SAG.");
        bannerMessages.add("Desde 2012 a 2025, la producción de vino en Chile ha mostrado ciclos de alzas y bajas, pero se mantiene en niveles altos.");

        // Iniciar / reiniciar la rotación de mensajes
        startBannerRotation();
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

        // 🍇 Click en la uva: detalle cuando hay consumo óptimo
        if (headerGif != null) {
            headerGif.setOnClickListener(v -> {
                if (hasOptimalWines) {
                    showOptimalWinesDialog();
                } else {
                    Toast.makeText(
                            HomeActivity.this,
                            "No hay botellas en consumo óptimo",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }

    }

    //•
    @SuppressLint("SetTextI18n")
    private void showOptimalWinesDialog() {
        if (currentOptimalWineNames == null || currentOptimalWineNames.isEmpty()) {
            Toast.makeText(
                    this,
                    "Tienes botellas en consumo óptimo, pero no pudimos cargar el detalle.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_optimal_wines);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setWindowAnimations(R.style.DialogAnimation);
        }

        TextView title = dialog.findViewById(R.id.dialogTitle);
        TextView description = dialog.findViewById(R.id.dialogDescription);
        TextView wineList = dialog.findViewById(R.id.dialogWineList);
        Button btnClose = dialog.findViewById(R.id.btnCloseDialog);
        Button btnOpenCellar = dialog.findViewById(R.id.btnOpenCellar);

        title.setText("Botellas en su punto óptimo de consumo 🍷");
        description.setVisibility(View.GONE);

        // 🔹 Lista: nombre corto + categoría
        StringBuilder builder = new StringBuilder();
        for (String displayText : currentOptimalWineNames) {
            builder.append("• ").append(displayText).append("\n");
        }
        wineList.setText(builder.toString().trim());

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnOpenCellar.setOnClickListener(v -> {
            dialog.dismiss();
            redirectToActivity(ViewCollectionActivity.class);
        });

        dialog.show();
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
                    String category = document.getString("category");

                    if (variety != null) {
                        // Capitalizamos para usar en gráficos y en el texto
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
                                        // 1️⃣ Nombre corto
                                        String shortName = getShortWineName(wineName);

                                        // 2️⃣ Armamos: nombre - variedad - categoría - año
                                        StringBuilder display = new StringBuilder(shortName);

                                        // variedad
                                        if (variety != null && !variety.trim().isEmpty()) {
                                            display.append(" - ").append(variety.trim());
                                        }

                                        // categoría (Reserva, Gran Reserva, etc.)
                                        if (category != null && !category.trim().isEmpty()) {
                                            display.append(" - ").append(category.trim());
                                        }

                                        // año (vintage)
                                        if (vintageStr != null && !vintageStr.trim().isEmpty()) {
                                            display.append(" - ").append(vintageStr.trim());
                                        }

                                        // 3️⃣ Guardamos el texto ya formateado
                                        optimalWineNames.add(display.toString());
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

                // 🔁 Actualizar mensajes y reactivar animación del banner
                updateBannerMessage(totalWines, totalCellarValue);

                // Actualizar lista global de vinos óptimos
                currentOptimalWineNames.clear();
                currentOptimalWineNames.addAll(optimalWineNames);

                // actualizar estado del GIF de uva
                boolean hasOptimal = !optimalWineNames.isEmpty();
                updateGrapeGifState(hasOptimal, optimalCount);


                // Notificación solo si hay vinos en consumo óptimo
                if (hasOptimal) {
                    sendOptimalConsumptionNotification(optimalWineNames);
                }
            } else {
                txtTotalWines.setText("-");
                txtOptimalWines.setText("-");
                updateGrapeGifState(false, 0); // no hay datos → asumimos sin consumo óptimo
                // Banner en modo "sin datos"
                updateBannerMessage(0, 0);
            }
        }).addOnFailureListener(e -> {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "Error cargando datos", Toast.LENGTH_SHORT).show();
            updateGrapeGifState(false, 0);
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

        LineData lineData = new LineData(dataSet);
        valueLineChart.setData(lineData);

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

    private void updateGrapeGifState(boolean hasOptimal, int optimalCount) {
        hasOptimalWines = hasOptimal;

        // Control de animación del GIF
        if (headerGif != null) {
            Drawable drawable = headerGif.getDrawable();
            if (drawable instanceof GifDrawable) {
                GifDrawable gifDrawable = (GifDrawable) drawable;

                if (hasOptimal) {
                    gifDrawable.start();
                } else {
                    gifDrawable.stop();
                    gifDrawable.seekToFrameAndGet(0);
                }
            }
        }


        // Mostrar u ocultar el badge
        if (tvOptimalBadge != null) {
            if (hasOptimal && optimalCount > 0) {
                tvOptimalBadge.setVisibility(View.VISIBLE);

                // Muestra el número hasta 9, luego "9+"
                String txt = optimalCount > 9 ? "9+" : String.valueOf(optimalCount);
                tvOptimalBadge.setText(txt);
            } else {
                tvOptimalBadge.setVisibility(View.GONE);
            }
        }

    }

    /**
     * Devuelve solo el "nombre corto" del vino.
     * Ej:
     *  "Terrazas de los Andes Cabernet Sauvignon Reserva" -> "Terrazas de los Andes"
     *  "Casillero del Diablo Carmenere Gran Reserva" -> "Casillero del Diablo"
     */
    private String getShortWineName(String fullName) {
        if (fullName == null) return "";

        String lower = fullName.toLowerCase();

        // Palabras donde normalmente empiezan la cepa o categoría
        String[] corteEn = {
                "cabernet", "merlot", "carmenere", "carmeñere", "syrah", "malbec",
                "pinot", "chardonnay", "sauvignon", "riesling", "viognier",
                "gran", "reserva", "estate", "limited", "selección", "selection"
        };

        // Buscamos la primera de estas palabras y cortamos antes
        int corteIndex = -1;
        for (String key : corteEn) {
            int idx = lower.indexOf(key);
            if (idx != -1) {
                if (corteIndex == -1 || idx < corteIndex) {
                    corteIndex = idx;
                }
            }
        }

        if (corteIndex > 0) {
            return fullName.substring(0, corteIndex).trim();
        } else {
            // Si no encontramos ninguna palabra clave, devolvemos el nombre tal cual
            return fullName.trim();
        }
    }


}