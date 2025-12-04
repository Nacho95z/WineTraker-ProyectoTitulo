package com.example.winertraker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.droidsonroids.gif.GifImageView;

public class DisplayImageAndText extends AppCompatActivity {

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon;

    // UI
    private ImageView capturedImageView, aiProgressGif;
    private GifImageView commentAiGif;
    private EditText recognizedTextEdit, nameEditText, vintageEditText, originEditText,
            percentageEditText, wineNameEditText, categoryEditText;
    private TextView commentEditText;
    private ProgressBar progressBar;
    private LinearLayout commentHeaderLayout;

    // Datos
    private Uri imageUri;
    private String recognizedText;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_image_and_text);

        // Ocultar ActionBar para usar nuestro header custom
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        recognizedText = "";

        // --- Drawer / Header ---
        drawerLayout = findViewById(R.id.drawerLayoutDetails);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        // --- Vistas de contenido ---
        capturedImageView = findViewById(R.id.capturedImageView);
        recognizedTextEdit = findViewById(R.id.recognizedTextEdit);
        nameEditText = findViewById(R.id.nameEditText);
        vintageEditText = findViewById(R.id.vintageEditText);
        originEditText = findViewById(R.id.originEditText);
        percentageEditText = findViewById(R.id.percentageEditText);
        wineNameEditText = findViewById(R.id.wineNameEditText);
        categoryEditText = findViewById(R.id.categoryEditText);

        Button saveButton = findViewById(R.id.buttonSave);
        Button discardButton = findViewById(R.id.buttonDiscard);

        progressBar = findViewById(R.id.progressBar);
        aiProgressGif = findViewById(R.id.aiProgressGif);

        // Bloque comentario IA
        commentHeaderLayout = findViewById(R.id.commentHeaderLayout);
        commentEditText = findViewById(R.id.commentEditText);
        commentAiGif = findViewById(R.id.commentAiGif);

        // Ocultar bloque comentario IA al inicio
        commentHeaderLayout.setVisibility(View.GONE);
        commentEditText.setVisibility(View.GONE);
        commentAiGif.setVisibility(View.GONE);

        // Abrir menú lateral
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Manejo de clics del menú lateral
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                redirectToActivity(HomeActivity.class);
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

        // Firebase
        storageRef = FirebaseStorage.getInstance().getReference();
        firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user != null ? user.getUid() : "unknown";

        // Datos desde Intent
        imageUri = getIntent().getParcelableExtra("imageUri");
        recognizedText = getIntent().getStringExtra("recognizedText");

        if (imageUri == null) {
            Toast.makeText(this, "No se encontró la imagen.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Mostrar imagen
        capturedImageView.setImageURI(imageUri);

        // Mostrar progreso mientras analizamos
        progressBar.setVisibility(View.VISIBLE);

        // OpenAI primero, OCR local como fallback
        WineLabelAnalyzer.analyzeImage(
                this,
                imageUri,
                (info, rawOcrText, error) -> runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (info != null) {
                        // Resultado desde OpenAI
                        wineNameEditText.setText(info.getWineName());
                        nameEditText.setText(info.getVariety());
                        vintageEditText.setText(info.getVintage());
                        originEditText.setText(info.getOrigin());
                        percentageEditText.setText(info.getPercentage());

                        String category = info.getCategory();
                        if (category != null && !category.isEmpty()) {
                            categoryEditText.setText(category);
                        }

                        recognizedText = info.getRawText();
                        if (recognizedText != null && !recognizedText.isEmpty()) {
                            recognizedTextEdit.setText(recognizedText);
                        }

                        // Comentario IA descriptivo del vino
                        String comment = info.getComment();
                        if (comment != null && !comment.isEmpty()) {
                            String formatted = "\"" + comment + "\"";   // «"texto"»
                            commentEditText.setText(formatted);
                            commentHeaderLayout.setVisibility(View.VISIBLE);
                            commentEditText.setVisibility(View.VISIBLE);
                            commentAiGif.setVisibility(View.VISIBLE);
                        } else {
                            commentHeaderLayout.setVisibility(View.GONE);
                            commentEditText.setVisibility(View.GONE);
                            commentAiGif.setVisibility(View.GONE);
                        }


                    } else if (rawOcrText != null) {
                        // Fallback: OCR local
                        recognizedText = rawOcrText;
                        recognizedTextEdit.setText(recognizedText);
                        autofillFields(recognizedText);

                    } else if (error != null) {
                        Log.e("DisplayImageAndText", "Error analizando imagen", error);
                        Toast.makeText(
                                DisplayImageAndText.this,
                                "No se pudo analizar la imagen.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
        );

        // Guardar
        saveButton.setOnClickListener(v -> {
            recognizedText = recognizedTextEdit.getText().toString().trim();
            String wineName = wineNameEditText.getText().toString().trim();
            String variety = nameEditText.getText().toString().trim();
            String vintage = vintageEditText.getText().toString().trim();
            String origin = originEditText.getText().toString().trim();
            String percentage = percentageEditText.getText().toString().trim();
            String category = categoryEditText.getText().toString().trim();
            String comment = commentEditText.getText().toString().trim();

            variety = normalizeVarietyCanonical(variety);
            nameEditText.setText(variety);

            if (!validateInputs(wineName, variety, vintage, origin, percentage, category)) {
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            saveDataToFirebase(wineName, variety, vintage, origin, percentage, category, comment);
        });

        // Descartar
        discardButton.setOnClickListener(v -> navigateToHome());
    }

    // --- Drawer helpers ---

    private void redirectToActivity(Class<?> activityClass) {
        Intent intent = new Intent(DisplayImageAndText.this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();

        getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("remember_session", false)
                .apply();

        Intent intent = new Intent(DisplayImageAndText.this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Si el drawer está abierto, lo cerramos primero
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- LÓGICA ORIGINAL (validaciones, guardado, etc.) ----------

    // Quita tildes/acentos para comparar de forma neutra
    private String stripAccents(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    // Devuelve una forma "canónica" para variedades conocidas
    private String normalizeVarietyCanonical(String variety) {
        if (variety == null) return "";
        String trimmed = variety.trim();
        String plain = stripAccents(trimmed).toLowerCase(Locale.ROOT);

        if (plain.contains("carmenere")) return "Carmenère";
        if (plain.contains("cabernet sauvignon")) return "Cabernet Sauvignon";
        if (plain.contains("merlot")) return "Merlot";
        if (plain.contains("syrah") || plain.contains("shiraz")) return "Syrah";
        if (plain.contains("pinot noir")) return "Pinot Noir";
        if (plain.contains("malbec")) return "Malbec";
        if (plain.contains("chardonnay")) return "Chardonnay";
        if (plain.contains("sauvignon blanc")) return "Sauvignon Blanc";

        return trimmed;
    }

    private boolean validateInputs(String wineName, String variety, String vintage,
                                   String origin, String percentage, String category) {

        if (recognizedText == null) recognizedText = "";

        if (wineName != null) wineName = wineName.trim();
        if (variety != null) variety = variety.trim();
        if (vintage != null) vintage = vintage.trim();
        if (origin != null) origin = origin.trim();
        if (percentage != null) percentage = percentage.trim();
        if (category != null) category = category.trim();

        variety = normalizeVarietyCanonical(variety);

        if (wineName.isEmpty() || variety.isEmpty() || vintage.isEmpty()
                || origin.isEmpty() || percentage.isEmpty()) {
            Toast.makeText(this, "Por favor complete todos los campos requeridos.", Toast.LENGTH_SHORT).show();
            return false;
        }

        String wineNamePattern = "^[\\p{L}0-9\\s'’.-]{3,}$";
        if (!wineName.matches(wineNamePattern)) {
            Toast.makeText(this,
                    "El nombre del vino debe tener al menos 3 caracteres y solo letras, números y símbolos simples.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!vintage.matches("\\d{4}")) {
            Toast.makeText(this, "Ingrese un año válido (4 dígitos).", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!percentage.matches("\\d+(\\.\\d+)?")) {
            Toast.makeText(this, "Ingrese un porcentaje de alcohol válido.", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            double alc = Double.parseDouble(percentage);
            if (alc < 0 || alc > 100) {
                Toast.makeText(this, "El porcentaje de alcohol debe estar entre 0 y 100.", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ingrese un porcentaje de alcohol válido.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validación inteligente de VARIEDAD (solo letras y espacios, mínimo 3 caracteres)
        String varietyPattern = "^[\\p{L}\\s]{3,}$";  // \p{L} = cualquier letra (con tildes, diéresis, etc.)

        if (!variety.matches(varietyPattern)) {
            Toast.makeText(this,
                    "La variedad debe contener solo letras y al menos 3 caracteres.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        String[] knownVarieties = {
                "Cabernet Sauvignon", "Merlot", "Carmenère", "Syrah", "Pinot Noir",
                "Malbec", "Cabernet Franc", "Grenache", "Tempranillo", "Sangiovese",
                "Chardonnay", "Sauvignon Blanc", "Riesling", "Viognier", "Gewürztraminer",
                "Semillón", "Pedro Ximénez", "Moscatel", "País", "Carignan"
        };

        boolean foundVariety = false;
        String normalizedVarietyPlain = stripAccents(variety).toLowerCase(Locale.ROOT);

        for (String v : knownVarieties) {
            String normalizedKnownPlain = stripAccents(v).toLowerCase(Locale.ROOT);
            if (normalizedKnownPlain.equals(normalizedVarietyPlain)) {
                foundVariety = true;
                break;
            }
        }

        if (!foundVariety) {
            Toast.makeText(this,
                    "La variedad \"" + variety + "\" no está en nuestra lista conocida. ¿Está seguro que es correcta?",
                    Toast.LENGTH_LONG).show();
        }

        if (category != null && !category.isEmpty()) {
            String[] knownCategories = {
                    "Reserva",
                    "Gran Reserva",
                    "Gran Reserva de los Andes",
                    "Reserva Especial",
                    "Edición Limitada",
                    "Estate Bottled"
            };

            boolean foundCategory = false;
            for (String c : knownCategories) {
                if (c.equalsIgnoreCase(category)) {
                    foundCategory = true;
                    break;
                }
            }

            if (!foundCategory) {
                Toast.makeText(this,
                        "La categoría \"" + category + "\" no está en nuestra lista conocida. ¿Está seguro que es correcta?",
                        Toast.LENGTH_LONG).show();
            }
        }

        return true;
    }

    private void autofillFields(String text) {
        String variety = identifyWine(text);
        if (variety != null) {
            nameEditText.setText(variety);
        }

        Pattern yearPattern = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher yearMatcher = yearPattern.matcher(text);
        if (yearMatcher.find()) {
            vintageEditText.setText(yearMatcher.group());
        }

        Pattern percentagePattern = Pattern.compile("\\b\\d+%\\b");
        Matcher percentageMatcher = percentagePattern.matcher(text);
        if (percentageMatcher.find()) {
            percentageEditText.setText(
                    percentageMatcher.group().replace("%", "").trim()
            );
        }

        String[] originKeywords = {"Maipo", "Colchagua", "Casablanca", "Limarí", "Itata",
                "Aconcagua", "Curicó", "Maule"};

        for (String origin : originKeywords) {
            if (text.toLowerCase().contains(origin.toLowerCase())) {
                originEditText.setText("Valle de " + origin);
                break;
            }
        }
    }

    private String identifyWine(String text) {
        String lower = text.toLowerCase();

        if (lower.contains("carmenere") || lower.contains("carmenère"))
            return "Carmenère";
        if (lower.contains("cabernet sauvignon"))
            return "Cabernet Sauvignon";
        if (lower.contains("cabernet") && !lower.contains("cabernet sauvignon"))
            return "Cabernet";
        if (lower.contains("merlot"))
            return "Merlot";
        if (lower.contains("syrah") || lower.contains("shiraz"))
            return "Syrah";
        if (lower.contains("pinot noir"))
            return "Pinot Noir";
        if (lower.contains("malbec"))
            return "Malbec";
        if (lower.contains("chardonnay"))
            return "Chardonnay";
        if (lower.contains("sauvignon blanc"))
            return "Sauvignon Blanc";

        return null;
    }

    private void saveDataToFirebase(String wineName, String variety, String vintage,
                                    String origin, String percentage, String category, String comment) {
        if (imageUri == null) {
            Toast.makeText(this, "No hay imagen para guardar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uniqueFileName = System.currentTimeMillis() + ".jpg";

        StorageReference imageRef = storageRef.child("images/" + userId + "/" + uniqueFileName);
        UploadTask uploadTask = imageRef.putFile(imageUri);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveToFirestore(uri.toString(), wineName, variety, vintage, origin, percentage, category, comment);
            }).addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(capturedImageView, "No se pudo obtener la URL de la imagen.", Snackbar.LENGTH_LONG).show();
            });
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Snackbar.make(capturedImageView, "Error al subir la imagen.", Snackbar.LENGTH_LONG).show();
        });
    }

    private void saveToFirestore(String imageUrl, String wineName, String variety,
                                 String vintage, String origin, String percentage, String category, String comment) {

        CollectionReference userCollection = firestore
                .collection("descriptions")
                .document(userId)
                .collection("wineDescriptions");

        Map<String, Object> imageData = new HashMap<>();
        imageData.put("imageUrl", imageUrl);
        imageData.put("recognizedText", recognizedText);
        imageData.put("wineName", wineName);
        imageData.put("variety", variety);
        imageData.put("vintage", vintage);
        imageData.put("origin", origin);
        imageData.put("percentage", percentage);
        imageData.put("category", category);
        imageData.put("comment", comment);

        userCollection.add(imageData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Snackbar.make(capturedImageView, "Datos guardados correctamente.", Snackbar.LENGTH_LONG).show();
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Snackbar.make(capturedImageView, "Error al guardar en Firestore.", Snackbar.LENGTH_LONG).show();
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(DisplayImageAndText.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
