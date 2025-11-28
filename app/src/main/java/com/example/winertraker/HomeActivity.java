package com.example.winertraker;

import android.Manifest; // IMPORTANTE: Para los permisos
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull; // Para @NonNull
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Para solicitar permisos
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat; // Para verificar permisos

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

    // Código para identificar la solicitud de permisos
    private static final int PERMISSION_REQUEST_CODE = 112;

    private FirebaseUser user;
    private TextView welcomeTextView, emailTextView, providerTextView, emailVerifiedTextView, uidTextView, collectionStatsTextView;
    private Button logoutButton, addBottleButton, viewCollectionButton;
    private FirebaseFirestore firestore;
    private String userId;
    private PieChart pieChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // --- NUEVO: Solicitar permisos automáticamente al abrir la pantalla ---
        checkAndRequestPermissions();
        // ----------------------------------------------------------------------

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
        welcomeTextView = findViewById(R.id.welcomeTextView);
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
        if (user.getDisplayName() != null) {
            welcomeTextView.setText("Bienvenido! " + user.getDisplayName());
        } else {
            welcomeTextView.setText("Bienvenido, Usuario");
        }

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

    // --- NUEVO MÉTODO: Verifica y pide permisos de una sola vez ---
    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();

        // 1. Revisar Cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // 2. Revisar Notificaciones (Solo si es Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // 3. Si falta alguno, pedirlo
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    // --- NUEVO MÉTODO: Maneja la respuesta del usuario ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Aquí puedes agregar lógica adicional si quieres reaccionar
            // Por ejemplo, mostrar un mensaje si rechazaron la cámara.

            // Verificamos respuestas
            Map<String, Integer> perms = new HashMap<>();
            if (grantResults.length > 0) {
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                }

                // Ejemplo: Si rechazó la cámara
                if (perms.containsKey(Manifest.permission.CAMERA) &&
                        perms.get(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "La cámara es necesaria para escanear botellas", Toast.LENGTH_LONG).show();
                }
            }
        }
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
        if (userId == null) return; // Evitar crash si no hay usuario

        CollectionReference collectionRef = firestore.collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        collectionRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                int totalWines = 0;
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

                // Update statistics TextView
                StringBuilder statsBuilder = new StringBuilder();
                statsBuilder.append("Total vinos en colección: ").append(totalWines).append("\n\n");
                for (Map.Entry<String, Integer> entry : wineVarietyCounts.entrySet()) {
                    statsBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                collectionStatsTextView.setText(statsBuilder.toString());

                // Update pie chart
                updatePieChart(wineVarietyCounts);

                // Send notification if there are optimal wines
                if (!optimalWineNames.isEmpty()) {
                    sendOptimalConsumptionNotification(optimalWineNames);
                }
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
            entries.add(new PieEntry(entry.getValue(), capitalize(entry.getKey())));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);

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
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.getDescription().setEnabled(false);

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

    private void sendOptimalConsumptionNotification(List<String> wineNames) {
        if (wineNames.isEmpty()) return;

        // VERIFICACIÓN DE SEGURIDAD MODIFICADA
        // En lugar de pedir permiso aquí (que puede cortar el flujo), verificamos si lo tenemos.
        // Si no lo tenemos, simplemente salimos del método para evitar crasheos.
        // Se asume que el usuario aceptó los permisos en el inicio (onCreate).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Si llegamos aquí y no tenemos permiso, no podemos notificar.
                return;
            }
        }

        StringBuilder notificationMessage = new StringBuilder("Los siguientes vinos están en su punto óptimo de consumo:\n");
        for (String wine : wineNames) {
            notificationMessage.append("- ").append(wine).append("\n");
        }

        // Cambia el Intent para llevar a ViewCollectionActivity sin usar FLAG_ACTIVITY_CLEAR_TASK
        Intent intent = new Intent(this, ViewCollectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Solo se asegura de iniciar como nueva actividad en la pila
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "wine_optimal_channel")
                .setSmallIcon(R.drawable.ic_wine) // Asegúrate de tener este ícono
                .setContentTitle("Consumo óptimo")
                .setContentText("Revisa tu colección para más detalles.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        try {
            notificationManager.notify(1, builder.build());
        } catch (SecurityException e) {
            // Bloque catch por seguridad en caso de que el permiso se revoque en tiempo real
            e.printStackTrace();
        }
    }


    private String capitalize(String text) {
        String[] words = text.split(" ");
        StringBuilder capitalized = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase()).append(" ");
            }
        }

        return capitalized.toString().trim();
    }
}