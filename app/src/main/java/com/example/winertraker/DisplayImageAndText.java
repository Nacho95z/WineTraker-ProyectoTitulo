package com.example.winertraker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class DisplayImageAndText extends AppCompatActivity {

    private ImageView capturedImageView;
    private EditText recognizedTextEdit, nameEditText, vintageEditText, originEditText, percentageEditText, wineNameEditText, categoryEditText;
    private Uri imageUri;
    private String recognizedText;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;
    private String userId;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_image_and_text);
        recognizedText = "";

        // Initialize views
        capturedImageView = findViewById(R.id.capturedImageView);
        recognizedTextEdit = findViewById(R.id.recognizedTextEdit);
        nameEditText = findViewById(R.id.nameEditText);
        vintageEditText = findViewById(R.id.vintageEditText);
        originEditText = findViewById(R.id.originEditText);
        percentageEditText = findViewById(R.id.percentageEditText);
        wineNameEditText = findViewById(R.id.wineNameEditText);
        Button saveButton = findViewById(R.id.buttonSave);
        Button discardButton = findViewById(R.id.buttonDiscard);
        progressBar = findViewById(R.id.progressBar);

        // Initialize Firebase
        storageRef = FirebaseStorage.getInstance().getReference();
        firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user != null ? user.getUid() : "unknown";

        // Get data from intent
        imageUri = getIntent().getParcelableExtra("imageUri");
        recognizedText = getIntent().getStringExtra("recognizedText");
        categoryEditText = findViewById(R.id.categoryEditText);

        // Si por alguna razón no llegó la URI, avisamos y salimos
        if (imageUri == null) {
            Toast.makeText(this, "No se encontró la imagen.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Mostrar imagen
        capturedImageView.setImageURI(imageUri);

        // Mostrar progreso mientras analizamos
        progressBar.setVisibility(View.VISIBLE);

        // 🔍 OpenAI primero, OCR local como fallback
        WineLabelAnalyzer.analyzeImage(
                this,
                imageUri,
                (info, rawOcrText, error) -> runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (info != null) {
                        //Resultado desde OpenAI
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


                    } else if (rawOcrText != null) {
                        // 🔁 Fallback: OCR local
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


        // Save button functionality
        saveButton.setOnClickListener(v -> {
            recognizedText = recognizedTextEdit.getText().toString().trim();
            String wineName = wineNameEditText.getText().toString().trim();
            String variety = nameEditText.getText().toString().trim();
            String vintage = vintageEditText.getText().toString().trim();
            String origin = originEditText.getText().toString().trim();
            String percentage = percentageEditText.getText().toString().trim();
            String category = categoryEditText.getText().toString().trim();


            if (!validateInputs(wineName, variety, vintage, origin, percentage, category)) {
                return;
            }


            progressBar.setVisibility(View.VISIBLE);
            saveDataToFirebase(wineName, variety, vintage, origin, percentage, category);
        });

        // Discard button functionality
        discardButton.setOnClickListener(v -> {
            Intent intent = new Intent(DisplayImageAndText.this, CaptureIMG.class);
            startActivity(intent);
            finish();
        });
    }

    private boolean validateInputs(String wineName, String variety, String vintage, String origin, String percentage, String category) {

        // Evitar null en recognizedText (aunque ya no es obligatorio)
        if (recognizedText == null) {
            recognizedText = "";
        }

        // Normalizar espacios
        if (wineName != null) wineName = wineName.trim();
        if (variety != null) variety = variety.trim();
        if (vintage != null) vintage = vintage.trim();
        if (origin != null) origin = origin.trim();
        if (percentage != null) percentage = percentage.trim();
        if (category != null) category = category.trim();

        // 1) Campos obligatorios (category es opcional)
        if (wineName.isEmpty() || variety.isEmpty() || vintage.isEmpty() ||
                origin.isEmpty() || percentage.isEmpty()) {
            Toast.makeText(this, "Por favor complete todos los campos requeridos.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 2) Validar NOMBRE del vino (texto razonable)
        String wineNamePattern = "^[\\p{L}0-9\\s'’.-]{3,}$"; // letras, números, espacios, ', ., -
        if (!wineName.matches(wineNamePattern)) {
            Toast.makeText(this,
                    "El nombre del vino debe tener al menos 3 caracteres y solo letras, números y símbolos simples.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // 3) Validar año (4 dígitos)
        if (!vintage.matches("\\d{4}")) {
            Toast.makeText(this, "Ingrese un año válido (4 dígitos).", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 4) Validar porcentaje de alcohol (0 a 100)
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

        // 5) Validación inteligente de VARIEDAD (solo letras, espacios y acentos, mínimo 3 caracteres)
        String varietyPattern = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]{3,}$";
        if (!variety.matches(varietyPattern)) {
            Toast.makeText(this,
                    "La variedad debe contener solo letras y al menos 3 caracteres.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // Lista de variedades conocidas (SOLO ADVERTENCIA, NO BLOQUEA)
        String[] knownVarieties = {
                "Cabernet Sauvignon", "Merlot", "Carmenere", "Syrah", "Pinot Noir",
                "Malbec", "Cabernet Franc", "Grenache", "Tempranillo", "Sangiovese",
                "Chardonnay", "Sauvignon Blanc", "Riesling", "Viognier", "Gewürztraminer",
                "Semillón", "Pedro Ximénez", "Moscatel", "País", "Carignan"
        };

        boolean foundVariety = false;
        for (String v : knownVarieties) {
            if (v.equalsIgnoreCase(variety)) {
                foundVariety = true;
                break;
            }
        }

        if (!foundVariety) {
            Toast.makeText(this,
                    "La variedad \"" + variety + "\" no está en nuestra lista conocida. ¿Está seguro que es correcta?",
                    Toast.LENGTH_LONG).show();
            // Solo aviso, NO return false
        }

        // 6) Categoría / línea: advertencia suave (opcional)
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
                // Solo advertimos, NO bloqueamos
            }
        }

        return true; // ✅ Todos los campos obligatorios son válidos
    }



    private void autofillFields(String text) {
        // 1) Autocomplete variety
        String variety = identifyWine(text);
        if (variety != null) {
            nameEditText.setText(variety);
        }

        // 2) Detect year
        Pattern yearPattern = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher yearMatcher = yearPattern.matcher(text);
        if (yearMatcher.find()) {
            vintageEditText.setText(yearMatcher.group());
        }

        // 3) Detect alcohol percentage
        Pattern percentagePattern = Pattern.compile("\\b\\d+%\\b");
        Matcher percentageMatcher = percentagePattern.matcher(text);
        if (percentageMatcher.find()) {
            percentageEditText.setText(
                    percentageMatcher.group().replace("%", "").trim()
            );
        }

        // 4) Autocomplete origin
        String[] originKeywords = {"Maipo", "Colchagua", "Casablanca", "Limarí", "Itata", "Aconcagua", "Curicó", "Maule"};

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
            return "Carmenere";

        if (lower.contains("cabernet sauvignon"))
            return "Cabernet Sauvignon";

        if (lower.contains("cabernet") && !lower.contains("cabernet sauvignon"))
            return "Cabernet"; // Caso raro, pero posible

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



    private void saveDataToFirebase(String wineName, String variety, String vintage, String origin, String percentage, String category) {
        if (imageUri == null) {
            Toast.makeText(this, "No hay imagen para guardar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uniqueFileName = System.currentTimeMillis() + ".jpg";

        StorageReference imageRef = storageRef.child("images/" + userId + "/" + uniqueFileName);
        UploadTask uploadTask = imageRef.putFile(imageUri);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveToFirestore(uri.toString(), wineName, variety, vintage, origin, percentage, category);
            }).addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(capturedImageView, "No se pudo obtener la URL de la imagen.", Snackbar.LENGTH_LONG).show();
            });
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Snackbar.make(capturedImageView, "Error al subir la imagen.", Snackbar.LENGTH_LONG).show();
        });
    }

    private void saveToFirestore(String imageUrl, String wineName, String variety, String vintage, String origin, String percentage, String category) {
        CollectionReference userCollection = firestore.collection("descriptions").document(userId).collection("wineDescriptions");

        Map<String, Object> imageData = new HashMap<>();
        imageData.put("imageUrl", imageUrl);
        imageData.put("recognizedText", recognizedText);
        imageData.put("wineName", wineName);
        imageData.put("variety", variety);
        imageData.put("vintage", vintage);
        imageData.put("origin", origin);
        imageData.put("percentage", percentage);
        imageData.put("category", category);


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
