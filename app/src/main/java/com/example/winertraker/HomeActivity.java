// HomeActivity.java
package com.example.winertraker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.github.mikephil.charting.animation.Easing;
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
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class HomeActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 112;

    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private String userId;

    // UI base
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon;

    // Banner
    private CardView infoBannerCard;
    private TextView bannerTextView;
    private ProgressBar bannerProgress;
    private ValueAnimator bannerProgressAnimator;
    private Runnable bannerRotationRunnable;

    private boolean bannerPaused = false;
    private long bannerCycleStartMs = 0L;
    private long bannerRemainingMs = BANNER_CYCLE_MS;

    private final List<String> bannerMessages = new ArrayList<>();
    private int currentBannerIndex = 0;
    private static final long BANNER_CYCLE_MS = 8000;
    private final android.os.Handler bannerHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // Accesos rápidos
    private CardView cardScanBottle;
    private CardView cardMyCellar;
    private CardView cardConsumed;
    private SwipeRefreshLayout swipeRefreshLayout;

    private TextView txtTotalWines;
    private TextView txtConsumedCount;

    // (se usa internamente, puede ir oculto en XML)
    private TextView txtOptimalWines;

    // GIF uva + badge
    private GifImageView headerGif;
    private TextView tvOptimalBadge;
    private boolean hasOptimalWines = false;

    private TextView headerTitle, headerEmail;
    private final List<String> currentOptimalWineNames = new ArrayList<>();
    private final List<String> currentOptimalWineIds = new ArrayList<>();

    // Charts
    private ViewPager2 chartsPager;
    private TabLayout chartsDots;
    private ChartsPagerAdapter chartsAdapter;
    private TextView txtChartInsight;

    private final Map<String, Integer> cachedVarietyCounts = new HashMap<>();
    private int[] cachedMonthCounts = new int[12];
    private double[] cachedMonthValues = new double[12];

    // Artículos
    private RecyclerView rvArticles;
    private ArticlesAdapter articlesAdapter;
    private final List<Article> articles = new ArrayList<>();

    private static final String[] MONTHS_FULL = {
            "Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
    };

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

        initializeViews();          // ✅ primero obtener headerTitle/headerEmail
        setupDrawerUserInfo();      // ✅ ahora sí se puede setear

        setupChartsPager();
        fixPagerSwipeConflicts();
        setupActions();
        setupArticlesSection();
        createNotificationChannel();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#B22034"));
            swipeRefreshLayout.setOnRefreshListener(this::loadCollectionStats);
        }

        loadCollectionStats();
        checkTermsAndConditions();
    }

    private void setupDrawerUserInfo() {
        if (user != null && headerTitle != null && headerEmail != null) {
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) displayName = "Amante del vino";
            headerTitle.setText(displayName);
            headerEmail.setText(user.getEmail());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null) loadCollectionStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bannerHandler.removeCallbacksAndMessages(null);
        stopBannerProgress();
    }

    // -------------------------------------------------------------
    // ⚖️ TÉRMINOS Y CONDICIONES
    // -------------------------------------------------------------

    private void checkTermsAndConditions() {
        SharedPreferences prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);
        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);
        if (!termsAccepted) showTermsDialog();
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_termconditions);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        CheckBox checkAccept = dialog.findViewById(R.id.checkAccept);
        Button btnAccept = dialog.findViewById(R.id.btnAcceptTerms);

        btnAccept.setEnabled(false);
        btnAccept.setAlpha(0.5f);

        checkAccept.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnAccept.setEnabled(isChecked);
            btnAccept.setAlpha(isChecked ? 1.0f : 0.5f);
        });

        btnAccept.setOnClickListener(v -> {
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

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        if (navigationView != null) {
            android.view.View headerView = navigationView.getHeaderView(0);
            headerTitle = headerView.findViewById(R.id.headerTitle);
            headerEmail = headerView.findViewById(R.id.headerEmail);
        }

        // Banner
        infoBannerCard = findViewById(R.id.infoBannerCard);
        bannerTextView = findViewById(R.id.bannerTextView);
        bannerProgress = findViewById(R.id.bannerProgress);

        // Cards
        cardScanBottle = findViewById(R.id.cardScanBottle);
        cardMyCellar = findViewById(R.id.cardMyCellar);
        cardConsumed = findViewById(R.id.cardConsumed);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Contadores
        txtTotalWines = findViewById(R.id.txtTotalWines);
        //txtConsumedCount = findViewById(R.id.txtConsumedCount);

        // oculto/soporte
        //txtOptimalWines = findViewById(R.id.txtOptimalWines);

        // GIF + badge
        headerGif = findViewById(R.id.headerGifLarge);
        tvOptimalBadge = findViewById(R.id.tvOptimalBadge);

        // Charts
        chartsPager = findViewById(R.id.chartsPager);
        chartsDots = findViewById(R.id.chartsDots);
        txtChartInsight = findViewById(R.id.txtChartInsight);

        // Artículos
        rvArticles = findViewById(R.id.rvArticles);
    }

    private void setupActions() {
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    Toast.makeText(HomeActivity.this, "Inicio", Toast.LENGTH_SHORT).show();

                } else if (id == R.id.nav_my_cellar) {
                    redirectToActivity(ViewCollectionActivity.class);

                } else if (id == R.id.nav_consumed) {
                    Toast.makeText(this, "Entrando a Consumidos", Toast.LENGTH_SHORT).show();
                    redirectToActivity(ConsumedWinesActivity.class);

                } else if (id == R.id.nav_settings) {
                    redirectToActivity(SettingsActivity.class);

                } else if (id == R.id.nav_logout) {
                    performLogout();
                }

                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        if (cardScanBottle != null) {
            cardScanBottle.setOnClickListener(v -> redirectToActivity(CaptureIMG.class));
        }

        if (cardMyCellar != null) {
            cardMyCellar.setOnClickListener(v -> redirectToActivity(ViewCollectionActivity.class));
        }

        if (cardConsumed != null) {
            cardConsumed.setOnClickListener(v -> redirectToActivity(ConsumedWinesActivity.class));
        }

        if (headerGif != null) {
            headerGif.setOnClickListener(v -> {
                if (hasOptimalWines) showOptimalWinesDialog();
                else Toast.makeText(HomeActivity.this, "No hay botellas en consumo óptimo", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // -------------------------------------------------------------
    // Banner (rotación + pausa sin reiniciar)
    // -------------------------------------------------------------

    private void startBannerRotation() {
        if (bannerTextView == null || bannerMessages.isEmpty()) return;

        if (bannerTextView.getText() == null || bannerTextView.getText().toString().trim().isEmpty()) {
            currentBannerIndex = 0;
            bannerTextView.setText(bannerMessages.get(0));
            bannerTextView.setAlpha(1f);
        }

        bannerHandler.removeCallbacksAndMessages(null);
        bannerPaused = false;

        bannerCycleStartMs = android.os.SystemClock.elapsedRealtime();
        bannerRemainingMs = BANNER_CYCLE_MS;

        startBannerProgressWithDuration(BANNER_CYCLE_MS);

        bannerRotationRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerPaused) return;
                if (bannerMessages.isEmpty() || bannerTextView == null) return;

                currentBannerIndex = (currentBannerIndex + 1) % bannerMessages.size();
                String msg = bannerMessages.get(currentBannerIndex);

                bannerCycleStartMs = android.os.SystemClock.elapsedRealtime();
                bannerRemainingMs = BANNER_CYCLE_MS;
                startBannerProgressWithDuration(BANNER_CYCLE_MS);

                bannerTextView.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction(() -> {
                            bannerTextView.setText(msg);
                            bannerTextView.animate().alpha(1f).setDuration(220).start();
                        })
                        .start();

                bannerHandler.postDelayed(this, BANNER_CYCLE_MS);
            }
        };

        bannerHandler.postDelayed(bannerRotationRunnable, BANNER_CYCLE_MS);

        setupBannerPauseTouch();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupBannerPauseTouch() {
        if (infoBannerCard == null) return;

        infoBannerCard.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pauseBanner();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resumeBanner();
                    v.performClick();
                    return true;
            }
            return false;
        });
    }

    private void resumeBanner() {
        if (!bannerPaused) return;
        bannerPaused = false;

        bannerCycleStartMs = android.os.SystemClock.elapsedRealtime() - (BANNER_CYCLE_MS - bannerRemainingMs);

        if (bannerProgressAnimator != null) bannerProgressAnimator.resume();
        else startBannerProgressWithDuration(bannerRemainingMs);

        if (bannerRotationRunnable != null) bannerHandler.postDelayed(bannerRotationRunnable, bannerRemainingMs);
    }

    private void pauseBanner() {
        if (bannerPaused) return;
        bannerPaused = true;

        long now = android.os.SystemClock.elapsedRealtime();
        long elapsed = now - bannerCycleStartMs;
        bannerRemainingMs = Math.max(0L, BANNER_CYCLE_MS - elapsed);

        if (bannerRotationRunnable != null) bannerHandler.removeCallbacks(bannerRotationRunnable);

        if (bannerProgressAnimator != null) bannerProgressAnimator.pause();
    }

    private void startBannerProgressWithDuration(long durationMs) {
        if (bannerProgress == null) return;

        stopBannerProgress();
        bannerProgress.setMax(1000);
        bannerProgress.setProgress(1000); // lleno

        bannerProgressAnimator = ValueAnimator.ofInt(0, 1000);
        bannerProgressAnimator.setDuration(Math.max(1, durationMs));
        bannerProgressAnimator.setInterpolator(new LinearInterpolator());
        bannerProgressAnimator.addUpdateListener(anim -> {
            if (bannerProgress != null) {
                int v = (int) anim.getAnimatedValue();
                bannerProgress.setProgress(1000 - v); // derecha→izquierda
            }
        });
        bannerProgressAnimator.start();
    }

    private void stopBannerProgress() {
        if (bannerProgressAnimator != null) {
            bannerProgressAnimator.cancel();
            bannerProgressAnimator = null;
        }
        if (bannerProgress != null) bannerProgress.setProgress(0);
    }

    private void updateBannerMessage(int totalWines, double totalCellarValue) {
        bannerMessages.clear();

        if (totalWines > 0) {
            bannerMessages.add("Tu bodega tiene " + totalWines + " botella(s) • Valor estimado " + formatCurrency(totalCellarValue));
        } else {
            bannerMessages.add("Comienza escaneando tu primera botella 🍷");
        }

        bannerMessages.add("Tip: guarda tus vinos lejos de la luz y con temperatura estable.");
        bannerMessages.add("Tip: los tintos jóvenes suelen ir mejor entre 14–16°C.");
        bannerMessages.add("Dato Chile: Maule lidera la producción nacional de vino (SAG).");

        startBannerRotation();
    }

    // -------------------------------------------------------------
    // Diálogo óptimos
    // -------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private void showOptimalWinesDialog() {
        if (currentOptimalWineNames.isEmpty()) {
            Toast.makeText(this, "Tienes botellas en consumo óptimo, pero no pudimos cargar el detalle.", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_optimal_wines);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(R.style.DialogAnimation);
        }

        TextView title = dialog.findViewById(R.id.dialogTitle);
        TextView description = dialog.findViewById(R.id.dialogDescription);
        TextView wineList = dialog.findViewById(R.id.dialogWineList);
        Button btnClose = dialog.findViewById(R.id.btnCloseDialog);
        Button btnOpenCellar = dialog.findViewById(R.id.btnOpenCellar);

        title.setText("Botellas en su punto óptimo de consumo 🍷");
        if (description != null) description.setVisibility(View.GONE);

        StringBuilder builder = new StringBuilder();
        for (String displayText : currentOptimalWineNames) {
            builder.append("• ").append(displayText).append("\n");
        }
        wineList.setText(builder.toString().trim());

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnOpenCellar.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(HomeActivity.this, ViewCollectionActivity.class);
            intent.putStringArrayListExtra("optimalWineIds", new ArrayList<>(currentOptimalWineIds));
            intent.putExtra("filterMode", "optimal");
            startActivity(intent);
        });

        dialog.show();
    }

    // -------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------

    private void performLogout() {
        App app = (App) getApplication();
        app.resetOptimalNotification();

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

    // -------------------------------------------------------------
    // Firestore Stats
    // -------------------------------------------------------------

    private void loadCollectionStats() {
        if (userId == null) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

            if (task.isSuccessful() && task.getResult() != null) {

                int totalWines = 0;
                int optimalCount = 0;
                int consumedCount = 0;

                Map<String, Integer> wineVarietyCounts = new HashMap<>();
                List<String> optimalWineNames = new ArrayList<>();
                List<String> optimalWineIdsTemp = new ArrayList<>();

                int[] monthCounts = new int[12];
                double[] monthValues = new double[12];
                double totalCellarValue = 0.0;

                for (QueryDocumentSnapshot document : task.getResult()) {

                    // ✅ Solo bodega: si está archivado/consumido no entra a gráficos ni valor bodega
                    Boolean archived = document.getBoolean("archived");
                    if (archived != null && archived) {
                        consumedCount++;
                        continue;
                    }

                    totalWines++;

                    String docId = document.getId();
                    String variety = document.getString("variety");
                    String vintageStr = document.getString("vintage");
                    String wineName = document.getString("wineName");
                    String category = document.getString("category");

                    // ✅ Variedades
                    if (variety != null) {
                        variety = capitalize(variety);
                        wineVarietyCounts.put(variety, wineVarietyCounts.getOrDefault(variety, 0) + 1);

                        // ✅ Óptimos
                        if (vintageStr != null) {
                            try {
                                int vintageYear = Integer.parseInt(vintageStr);
                                if (isInPeakNow(variety, category, vintageYear)) {
                                    optimalCount++;
                                    if (wineName != null) {
                                        String shortName = getShortWineName(wineName);

                                        StringBuilder display = new StringBuilder(shortName);
                                        if (category != null && !category.trim().isEmpty()) {
                                            display.append(" - ").append(category.trim());
                                        }
                                        if (!vintageStr.trim().isEmpty()) {
                                            display.append(" - ").append(vintageStr.trim());
                                        }

                                        optimalWineNames.add(display.toString());
                                        optimalWineIdsTemp.add(docId);
                                    }
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // ✅ Históricos: si createdAt viene null, lo asignamos al mes actual para no perder vinos
                    Timestamp ts = document.getTimestamp("createdAt");
                    Calendar cal = Calendar.getInstance();
                    if (ts != null) {
                        cal.setTime(ts.toDate());
                    }
                    int monthIndex = cal.get(Calendar.MONTH); // siempre 0..11
                    if (monthIndex >= 0 && monthIndex < 12) {
                        monthCounts[monthIndex]++; // ✅ cuenta siempre
                    }

                    // ✅ Precio: soporta Number, "9000", "$9.000", "CLP 9.000", etc.
                    Double price = null;
                    Object priceObj = document.get("price");

                    if (priceObj instanceof Number) {
                        price = ((Number) priceObj).doubleValue();
                    } else if (priceObj instanceof String) {
                        try {
                            String s = ((String) priceObj).trim()
                                    .replace("CLP", "")
                                    .replace("$", "")
                                    .replace(" ", "")
                                    .replace(".", "")   // miles
                                    .replace(",", "."); // decimales si existieran
                            if (!s.isEmpty()) price = Double.parseDouble(s);
                        } catch (Exception ignored) {}
                    }

                    if (price != null) {
                        totalCellarValue += price;
                        if (monthIndex >= 0 && monthIndex < 12) {
                            monthValues[monthIndex] += price; // ✅ valor siempre cae en algún mes
                        }
                    }
                }

                if (txtTotalWines != null) txtTotalWines.setText(String.valueOf(totalWines));
                if (txtConsumedCount != null) txtConsumedCount.setText(String.valueOf(consumedCount));
                if (txtOptimalWines != null) txtOptimalWines.setText(String.valueOf(optimalCount)); // soporte

                cachedVarietyCounts.clear();
                cachedVarietyCounts.putAll(wineVarietyCounts);
                cachedMonthCounts = monthCounts;
                cachedMonthValues = monthValues;

                if (chartsAdapter != null) chartsAdapter.notifyDataSetChanged();
                if (chartsPager != null) updateInsightForPage(chartsPager.getCurrentItem());

                updateBannerMessage(totalWines, totalCellarValue);

                currentOptimalWineNames.clear();
                currentOptimalWineNames.addAll(optimalWineNames);

                currentOptimalWineIds.clear();
                currentOptimalWineIds.addAll(optimalWineIdsTemp);

                boolean hasOptimal = !optimalWineNames.isEmpty();
                updateGrapeGifState(hasOptimal, optimalCount);

                App app = (App) getApplication();
                if (hasOptimal && !app.isOptimalNotificationSent()) {
                    sendOptimalConsumptionNotification(optimalWineNames);
                    app.setOptimalNotificationSent(true);
                }

            } else {
                if (txtTotalWines != null) txtTotalWines.setText("-");
                if (txtConsumedCount != null) txtConsumedCount.setText("-");
                if (txtOptimalWines != null) txtOptimalWines.setText("-");

                cachedVarietyCounts.clear();
                cachedMonthCounts = new int[12];
                cachedMonthValues = new double[12];
                if (chartsAdapter != null) chartsAdapter.notifyDataSetChanged();

                updateGrapeGifState(false, 0);
                updateBannerMessage(0, 0);
                if (chartsPager != null) updateInsightForPage(chartsPager.getCurrentItem());
            }

        }).addOnFailureListener(e -> {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "Error cargando datos", Toast.LENGTH_SHORT).show();

            cachedVarietyCounts.clear();
            cachedMonthCounts = new int[12];
            cachedMonthValues = new double[12];
            if (chartsAdapter != null) chartsAdapter.notifyDataSetChanged();

            updateGrapeGifState(false, 0);
            if (chartsPager != null) updateInsightForPage(chartsPager.getCurrentItem());
        });
    }

    private boolean isInPeakNow(String variety, String category, int vintageYear) {
        PeakWindowCalculator.PeakWindow w =
                PeakWindowCalculator.calculate(variety, category, vintageYear);

        int now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        return now >= w.startYear && now <= w.endYear;
    }



    // -------------------------------------------------------------
    // Charts
    // -------------------------------------------------------------

    private void setupChartsPager() {
        if (chartsPager == null) return;

        chartsAdapter = new ChartsPagerAdapter(new ChartsPagerAdapter.Binder() {
            @Override public void bindPie(PieChart chart) { updatePieChart(chart, cachedVarietyCounts); }
            @Override public void bindBar(BarChart chart) { updateBarChart(chart, cachedMonthCounts); }
            @Override public void bindLine(LineChart chart) { updateValueLineChart(chart, cachedMonthValues); }
        });

        chartsPager.setAdapter(chartsAdapter);
        chartsPager.setOffscreenPageLimit(2);

        chartsPager.setClipToPadding(false);
        chartsPager.setClipChildren(false);

        View child = chartsPager.getChildAt(0);
        if (child instanceof RecyclerView) {
            ((RecyclerView) child).setClipToPadding(false);
        }

        chartsPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setScaleY(0.95f + (1 - absPos) * 0.05f);
            page.setAlpha(0.7f + (1 - absPos) * 0.3f);
        });

        chartsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateInsightForPage(position);
            }
        });

        if (chartsDots != null) {
            new TabLayoutMediator(chartsDots, chartsPager, (tab, position) -> tab.setIcon(R.drawable.dot_selector)).attach();
        }

        updateInsightForPage(chartsPager.getCurrentItem());
    }

    private void updatePieChart(PieChart chart, Map<String, Integer> wineVarietyCounts) {
        if (chart == null) return;

        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), capitalize(entry.getKey())));
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("Sin datos para mostrar.");
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return String.valueOf((int) value); }
        });

        chart.setData(data);
        chart.setUsePercentValues(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);

        chart.setEntryLabelColor(Color.BLACK);
        chart.setEntryLabelTextSize(10f);

        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);

        chart.animateY(900, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private void updateBarChart(BarChart chart, int[] monthCounts) {
        if (chart == null) return;

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (monthCounts[i] > 0) entries.add(new BarEntry(i, monthCounts[i]));
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("Sin registros suficientes para mostrar el historial.");
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        List<Integer> barColors = new ArrayList<>();
        barColors.add(Color.parseColor("#B22034"));
        barColors.add(Color.parseColor("#FF9800"));
        barColors.add(Color.parseColor("#FFEB3B"));
        barColors.add(Color.parseColor("#4CAF50"));
        barColors.add(Color.parseColor("#2196F3"));
        barColors.add(Color.parseColor("#9C27B0"));
        barColors.add(Color.parseColor("#009688"));
        barColors.add(Color.parseColor("#E91E63"));
        barColors.add(Color.parseColor("#3F51B5"));
        barColors.add(Color.parseColor("#CDDC39"));
        barColors.add(Color.parseColor("#FF5722"));
        barColors.add(Color.parseColor("#795548"));
        dataSet.setColors(barColors);

        dataSet.setValueTextColor(Color.parseColor("#B22034"));
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return String.valueOf((int) value); }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chart.setData(data);

        XAxis xAxis = chart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(MONTHS_FULL));
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setTextSize(10f);
        xAxis.setAvoidFirstLastClipping(true);
        chart.getXAxis().setDrawAxisLine(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return String.valueOf((int) value); }
        });

        chart.getAxisRight().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);

        chart.setExtraOffsets(14f, 8f, 14f, 12f);
        chart.animateY(800, Easing.EaseOutCubic);
        chart.invalidate();
    }

    private void updateValueLineChart(LineChart chart, double[] monthValues) {
        if (chart == null) return;

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (monthValues[i] > 0) entries.add(new Entry(i, (float) monthValues[i]));
        }

        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText("Sin información de valor para mostrar.");
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        int lineColor = Color.parseColor("#1E88E5");
        dataSet.setColor(lineColor);
        dataSet.setCircleColor(lineColor);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setDrawCircleHole(true);

        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(this, R.drawable.area_gradient));
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setDrawVerticalHighlightIndicator(false);

        chart.setData(new LineData(dataSet));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(MONTHS_FULL));
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setTextSize(10f);
        chart.getXAxis().setDrawAxisLine(false);
        xAxis.setAvoidFirstLastClipping(true);

        float minX = entries.get(0).getX();
        float maxX = entries.get(entries.size() - 1).getX();
        xAxis.setAxisMinimum(minX);
        xAxis.setAxisMaximum(maxX + 0f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) { return formatCurrency(value); }
        });

        chart.getAxisRight().setEnabled(false);

        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setHighlightPerTapEnabled(true);
        chart.setHighlightPerDragEnabled(true);

        chart.setExtraOffsets(0f, 0f, 0f, 16f);
        chart.animateX(900, Easing.EaseInOutSine);

        ValueMarkerView marker = new ValueMarkerView(
                this,
                R.layout.marker_value,
                MONTHS_FULL,
                this::formatCurrency
        );
        chart.setMarker(marker);

        chart.invalidate();
    }

    // -------------------------------------------------------------
    // Insights
    // -------------------------------------------------------------

    private void updateInsightForPage(int position) {
        if (txtChartInsight == null) return;

        if (position == 0) {
            VarietyInsight vi = getTopVarietyInsight(cachedVarietyCounts);
            if (vi.total <= 0) {
                txtChartInsight.setText("Aún no hay datos de variedad. Escanea tu primera botella 🍷");
            } else {
                int pct = (int) Math.round((vi.count * 100.0) / vi.total);
                txtChartInsight.setText(vi.name + " representa el " + pct + "% de tu bodega (" + vi.count + " de " + vi.total + ").");
            }

        } else if (position == 1) {
            RecentMonthCountInsight mi = getMostRecentMonthWithCount(cachedMonthCounts);
            if (mi.count <= 0) {
                txtChartInsight.setText("Aún no hay registros mensuales suficientes.");
            } else {
                txtChartInsight.setText("Tu último mes con registros fue " + mi.monthName + " con " + mi.count + " botella(s) registrada(s).");
            }

        } else {
            RecentMonthValueInsight vi = getMostRecentMonthWithValue(cachedMonthValues);
            if (vi.value <= 0) {
                txtChartInsight.setText("Aún no hay historial de valor para mostrar.");
            } else {
                txtChartInsight.setText("Tu último mes con valor registrado fue " + vi.monthName + ": " + formatCurrency(vi.value) + ".");
            }
        }
    }

    private static class VarietyInsight { String name; int count; int total; }

    private VarietyInsight getTopVarietyInsight(Map<String, Integer> map) {
        VarietyInsight out = new VarietyInsight();
        out.name = "—";
        out.count = 0;
        out.total = 0;

        if (map == null || map.isEmpty()) return out;

        for (Map.Entry<String, Integer> e : map.entrySet()) {
            int v = (e.getValue() == null) ? 0 : e.getValue();
            out.total += v;
            if (v > out.count) {
                out.count = v;
                out.name = e.getKey();
            }
        }
        return out;
    }

    private static class RecentMonthCountInsight { String monthName; int count; }

    private RecentMonthCountInsight getMostRecentMonthWithCount(int[] monthCounts) {
        RecentMonthCountInsight out = new RecentMonthCountInsight();
        out.monthName = "—";
        out.count = 0;

        if (monthCounts == null || monthCounts.length < 12) return out;

        for (int i = 11; i >= 0; i--) {
            if (monthCounts[i] > 0) {
                out.count = monthCounts[i];
                out.monthName = MONTHS_FULL[i];
                break;
            }
        }
        return out;
    }

    private static class RecentMonthValueInsight { String monthName; double value; }

    private RecentMonthValueInsight getMostRecentMonthWithValue(double[] monthValues) {
        RecentMonthValueInsight out = new RecentMonthValueInsight();
        out.monthName = "—";
        out.value = 0.0;

        if (monthValues == null || monthValues.length < 12) return out;

        for (int i = 11; i >= 0; i--) {
            if (monthValues[i] > 0) {
                out.value = monthValues[i];
                out.monthName = MONTHS_FULL[i];
                break;
            }
        }
        return out;
    }

    // -------------------------------------------------------------
    // Artículos
    // -------------------------------------------------------------

    private void setupArticlesSection() {
        if (rvArticles == null) return;

        rvArticles.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvArticles.setNestedScrollingEnabled(false);
        rvArticles.setPadding(8, 0, 8, 0);
        rvArticles.setClipToPadding(false);

        articles.clear();

        articles.add(new Article(
                "a1",
                "Cómo leer una etiqueta de vino chileno (Denominación de Origen)",
                "Una guía para entender denominación de origen, valle, cepa y año sin enredos.",
                "Wines of Chile / Guía enológica",
                "2 min",
                "La etiqueta de un vino chileno entrega información clave sobre su origen, estilo y potencial de guarda. Saber interpretarla permite tomar mejores decisiones de compra y consumo.\n\n" +
                        "• Denominación de Origen (D.O.): indica el origen geográfico declarado del vino.\n\n" +
                        "• Valle o Región: aporta pistas sobre el estilo del vino.\n\n" +
                        "• Cepa: señala la variedad de uva utilizada.\n\n" +
                        "• Añada (año): corresponde al año de cosecha y afecta potencial de envejecimiento.\n",
                "",
                R.drawable.wine_article_1
        ));

        articles.add(new Article(
                "a2",
                "Maridaje: El arte de combinar vino y gastronomía",
                "Principios simples para equilibrar sabores entre vino y comida.",
                "Decanter · Guía de maridaje",
                "3 min",
                "• Intensidad: ligero con ligero, intenso con intenso.\n\n" +
                        "• Acidez: ayuda con comidas grasas o cremosas.\n\n" +
                        "• Taninos: se suavizan con proteínas.\n\n" +
                        "• Dulzor: el vino debe ser igual o más dulce que el plato.\n",
                "",
                R.drawable.wine_article_2
        ));

        articles.add(new Article(
                "a3",
                "Guarda y envejecimiento: cuándo esperar y cuándo abrir un vino",
                "Cómo distinguir vinos de consumo temprano vs. potencial de guarda.",
                "Wine Enthusiast · Guía de guarda",
                "3 min",
                "• No todos los vinos están hechos para guardarse.\n\n" +
                        "• Vinos con potencial de guarda: estructura, acidez y taninos.\n\n" +
                        "• Condiciones de guarda: 12–15 °C, sin luz directa.\n",
                "",
                R.drawable.wine_article_3
        ));

        articles.add(new Article(
                "a4",
                "Temperatura de servicio: el detalle que cambia completamente un vino",
                "A qué temperatura servir cada tipo de vino.",
                "Wine Spectator · Guía de servicio",
                "3 min",
                "• Espumantes: 6–8 °C\n" +
                        "• Blancos ligeros: 8–10 °C\n" +
                        "• Blancos con cuerpo: 10–12 °C\n" +
                        "• Tintos jóvenes: 14–16 °C\n" +
                        "• Tintos con guarda: 16–18 °C\n",
                "",
                R.drawable.wine_article_4
        ));

        articlesAdapter = new ArticlesAdapter(articles);
        rvArticles.setAdapter(articlesAdapter);
        articlesAdapter.notifyDataSetChanged();
    }

    // -------------------------------------------------------------
    // Helpers negocio + UI
    // -------------------------------------------------------------

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
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendOptimalConsumptionNotification(List<String> wineNames) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        if (wineNames == null || wineNames.isEmpty()) return;

        Intent intent = new Intent(this, ViewCollectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "wine_optimal_channel")
                .setSmallIcon(R.drawable.ic_bottle)
                .setContentTitle("¡Tienes vinos listos!")
                .setContentText("Tienes " + wineNames.size() + " botellas en su punto óptimo.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(1, builder.build());
    }

    private void redirectToActivity(Class<?> activityClass) {
        startActivity(new Intent(HomeActivity.this, activityClass));
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    private String formatCurrency(double value) {
        if (value <= 0) return "$0";
        DecimalFormat df = new DecimalFormat("$###,###");
        return df.format(value);
    }

    private void updateGrapeGifState(boolean hasOptimal, int optimalCount) {
        hasOptimalWines = hasOptimal;

        if (headerGif != null) {
            Drawable drawable = headerGif.getDrawable();
            if (drawable instanceof GifDrawable) {
                GifDrawable gifDrawable = (GifDrawable) drawable;
                if (hasOptimal) gifDrawable.start();
                else {
                    gifDrawable.stop();
                    gifDrawable.seekToFrameAndGet(0);
                }
            }
        }

        if (tvOptimalBadge != null) {
            if (hasOptimal && optimalCount > 0) {
                tvOptimalBadge.setVisibility(View.VISIBLE);
                String txt = optimalCount > 9 ? "9+" : String.valueOf(optimalCount);
                tvOptimalBadge.setText(txt);
            } else {
                tvOptimalBadge.setVisibility(View.GONE);
            }
        }
    }

    private String getShortWineName(String fullName) {
        if (fullName == null) return "";
        String lower = fullName.toLowerCase();

        String[] corteEn = {
                "cabernet", "merlot", "carmenere", "carmeñere", "syrah", "malbec",
                "pinot", "chardonnay", "sauvignon", "riesling", "viognier",
                "gran", "reserva", "estate", "limited", "selección", "selection"
        };

        int corteIndex = -1;
        for (String key : corteEn) {
            int idx = lower.indexOf(key);
            if (idx != -1) {
                if (corteIndex == -1 || idx < corteIndex) corteIndex = idx;
            }
        }

        if (corteIndex > 0) return fullName.substring(0, corteIndex).trim();
        return fullName.trim();
    }

    private void fixPagerSwipeConflicts() {
        if (chartsPager == null) return;

        chartsPager.setOnTouchListener(new View.OnTouchListener() {
            float startX = 0f, startY = 0f;
            boolean isHorizontal = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        isHorizontal = false;
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);

                        if (!isHorizontal && dx > dy && dx > 12) isHorizontal = true;
                        v.getParent().requestDisallowInterceptTouchEvent(isHorizontal);
                        return false;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                }
                return false;
            }
        });
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
