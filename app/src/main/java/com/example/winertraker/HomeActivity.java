package com.example.winertraker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
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
    private FirebaseUser user;
    private TextView emailTextView, providerTextView, emailVerifiedTextView, uidTextView, collectionStatsTextView;
    private Button logoutButton, addBottleButton, viewCollectionButton;
    private FirebaseFirestore firestore;
    private String userId;
    private PieChart pieChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Auth
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        if (user == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        userId = user.getUid();

        // Initialize views
        emailTextView = findViewById(R.id.emailTextView);
        providerTextView = findViewById(R.id.providerTextView);
        emailVerifiedTextView = findViewById(R.id.emailVerifiedTextView);
        uidTextView = findViewById(R.id.uidTextView);
        logoutButton = findViewById(R.id.logoutButton);
        addBottleButton = findViewById(R.id.addBottleButton);
        viewCollectionButton = findViewById(R.id.viewCollectionButton);
        collectionStatsTextView = findViewById(R.id.collectionStatsTextView);
        pieChart = findViewById(R.id.pieChart);

        // Display user information
        emailTextView.setText("Email: " + user.getEmail());
        emailVerifiedTextView.setText("Email Verified: " + user.isEmailVerified());
        uidTextView.setText("UID: " + userId);

        String provider = (user.getDisplayName() != null) ? user.getDisplayName() : "Not Available";
        providerTextView.setText("Name: " + provider);

        // Logout button
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(HomeActivity.this, AuthActivity.class));
            finish();
        });

        // Navigate to CaptureIMG or ViewCollection
        addBottleButton.setOnClickListener(v -> redirectToActivity(CaptureIMG.class));
        viewCollectionButton.setOnClickListener(v -> redirectToActivity(ViewCollectionActivity.class));

        // Create notification channel
        createNotificationChannel();

        // Load collection stats
        loadCollectionStats();
    }

    private void redirectToActivity(Class<?> activityClass) {
        Intent intent = new Intent(HomeActivity.this, activityClass);
        startActivity(intent);
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

    private void loadCollectionStats() {
        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int totalWines = 0;
                Map<String, Integer> wineVarietyCounts = new HashMap<>();
                boolean optimalConsumptionFound = false;

                for (QueryDocumentSnapshot document : task.getResult()) {
                    totalWines++;
                    String variety = document.getString("variety");
                    String vintageStr = document.getString("vintage");

                    if (variety != null) {
                        wineVarietyCounts.put(variety, wineVarietyCounts.getOrDefault(variety, 0) + 1);

                        if (vintageStr != null) {
                            try {
                                int vintageYear = Integer.parseInt(vintageStr);
                                if (isOptimalForConsumption(variety, vintageYear)) {
                                    optimalConsumptionFound = true;
                                }
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // Update statistics TextView
                StringBuilder statsBuilder = new StringBuilder();
                statsBuilder.append("Total vinos: ").append(totalWines).append("\n");
                for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
                    statsBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                collectionStatsTextView.setText(statsBuilder.toString());

                // Update the pie chart
                updatePieChart(wineVarietyCounts);

                // Send notification if optimal wines are found
                if (optimalConsumptionFound) {
                    sendOptimalConsumptionNotification();
                }
            } else {
                collectionStatsTextView.setText("No se pudo cargar la colección.");
            }
        }).addOnFailureListener(e -> {
            collectionStatsTextView.setText("Error al cargar estadísticas.");
        });
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

    private void sendOptimalConsumptionNotification() {
        // Check for POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
        }

        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "wine_optimal_channel")
                .setSmallIcon(R.drawable.ic_wine)
                .setContentTitle("Consumo óptimo")
                .setContentText("¡Tienes vinos en su punto óptimo de consumo! Revisa tu colección.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendOptimalConsumptionNotification();
            } else {
                Toast.makeText(this, "Permiso de notificaciones denegado.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updatePieChart(Map<String, Integer> wineVarietyCounts) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Variedades de Vino");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);

        pieChart.setUsePercentValues(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.getDescription().setEnabled(false);

        pieChart.animateXY(1400, 1400);
        pieChart.invalidate();
    }
}
