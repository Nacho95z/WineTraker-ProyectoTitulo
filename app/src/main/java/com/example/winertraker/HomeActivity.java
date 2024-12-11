package com.example.winertraker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
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

        // Edge-to-Edge configuration
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        // Load collection stats
        loadCollectionStats();
    }

    private void redirectToActivity(Class<?> activityClass) {
        Intent intent = new Intent(HomeActivity.this, activityClass);
        startActivity(intent);
    }

    private void loadCollectionStats() {
        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int totalWines = 0;
                Map<String, Integer> wineVarietyCounts = new HashMap<>();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    totalWines++;
                    String variety = document.getString("variety");
                    if (variety != null) {
                        wineVarietyCounts.put(variety, wineVarietyCounts.getOrDefault(variety, 0) + 1);
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
            } else {
                collectionStatsTextView.setText("No se pudo cargar la colección.");
            }
        }).addOnFailureListener(e -> {
            collectionStatsTextView.setText("Error al cargar estadísticas.");
        });
    }

    private void updatePieChart(Map<String, Integer> wineVarietyCounts) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Variedades de Vino");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS); // Colores predefinidos
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);

        // Configuración del gráfico
        pieChart.setUsePercentValues(false); // Mostrar valores absolutos en lugar de porcentajes
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.getDescription().setEnabled(false);

        // Animación
        pieChart.animateXY(1400, 1400); // Animación en X e Y

        // Refrescar gráfico
        pieChart.invalidate();
    }

}