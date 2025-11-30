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
    private EditText recognizedTextEdit, nameEditText, vintageEditText, originEditText, percentageEditText, wineNameEditText;
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
                        // ✅ Resultado desde OpenAI
                        wineNameEditText.setText(info.getWineName());
                        nameEditText.setText(info.getVariety());
                        vintageEditText.setText(info.getVintage());
                        originEditText.setText(info.getOrigin());
                        percentageEditText.setText(info.getPercentage());

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

            if (!validateInputs(wineName, variety, vintage, origin, percentage)) {
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            saveDataToFirebase(wineName, variety, vintage, origin, percentage);
        });

        // Discard button functionality
        discardButton.setOnClickListener(v -> {
            Intent intent = new Intent(DisplayImageAndText.this, CaptureIMG.class);
            startActivity(intent);
            finish();
        });
    }

    private boolean validateInputs(String wineName, String variety, String vintage, String origin, String percentage) {
        String[] allowedVarieties = {
                "Pinot Noir", "Gamay", "Merlot", "Tempranillo",
                "Cabernet Sauvignon", "Syrah", "Sauvignon Blanc",
                "Riesling", "Chardonnay", "Viognier"
        };

        if (recognizedText == null) {
            recognizedText = "";
        }

        // Verificar si los campos están vacíos
        if (recognizedText.isEmpty() || wineName.isEmpty() || variety.isEmpty()
                || vintage.isEmpty() || origin.isEmpty() || percentage.isEmpty()) {
            Toast.makeText(this, "Por favor complete todos los campos requeridos.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar el año de cosecha (debe ser un número de 4 dígitos)
        if (!vintage.matches("\\d{4}")) {
            Toast.makeText(this, "Ingrese un año válido (4 dígitos).", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar el porcentaje de alcohol (debe estar entre 0 y 100)
        if (!percentage.matches("\\d+(\\.\\d+)?") || Double.parseDouble(percentage) > 100) {
            Toast.makeText(this, "Ingrese un porcentaje válido entre 0 y 100.", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Validar que la variedad esté en el listado de variedades permitidas
        boolean isValidVariety = false;
        for (String allowedVariety : allowedVarieties) {
            if (allowedVariety.equalsIgnoreCase(variety)) { // Comparación insensible a mayúsculas/minúsculas
                isValidVariety = true;
                break;
            }
        }
        if (!isValidVariety) {
            Toast.makeText(this, "La variedad ingresada no es válida. Por favor, ingrese una de las siguientes: " +
                    String.join(", ", allowedVarieties), Toast.LENGTH_LONG).show();
            return false;
        }

        return true; // Todos los campos son válidos
    }

    private void autofillFields(String text) {
        String[] wineKeywords = {"cabernet", "merlot", "chardonnay", "sauvignon blanc", "pinot noir", "malbec", "carmenere"};
        String[] originKeywords = {"Maipo", "Colchagua", "Casablanca", "Limarí", "Itata", "Aconcagua", "Curicó", "Maule"};

        // Autocomplete variety
        String variety = identifyWine(text);
        if (variety != null) {
            nameEditText.setText(variety);
        }

        // Detect year
        Pattern yearPattern = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher yearMatcher = yearPattern.matcher(text);
        if (yearMatcher.find()) {
            vintageEditText.setText(yearMatcher.group());
        }

        // Detect alcohol percentage
        Pattern percentagePattern = Pattern.compile("\\b\\d+%\\b");
        Matcher percentageMatcher = percentagePattern.matcher(text);
        if (percentageMatcher.find()) {
            percentageEditText.setText(percentageMatcher.group().replace("%", "").trim());
        }

        // Autocomplete origin
        for (String origin : originKeywords) {
            if (text.toLowerCase().contains(origin.toLowerCase())) {
                originEditText.setText("Valle de " + origin);
                break;
            }
        }
    }

    private String identifyWine(String text) {
        for (String keyword : new String[]{"cabernet", "merlot", "chardonnay", "sauvignon blanc", "pinot noir", "malbec", "carmenere"}) {
            if (text.toLowerCase().contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private void saveDataToFirebase(String wineName, String variety, String vintage, String origin, String percentage) {
        if (imageUri == null) {
            Toast.makeText(this, "No hay imagen para guardar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uniqueFileName = System.currentTimeMillis() + ".jpg";

        StorageReference imageRef = storageRef.child("images/" + userId + "/" + uniqueFileName);
        UploadTask uploadTask = imageRef.putFile(imageUri);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveToFirestore(uri.toString(), wineName, variety, vintage, origin, percentage);
            }).addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(capturedImageView, "No se pudo obtener la URL de la imagen.", Snackbar.LENGTH_LONG).show();
            });
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Snackbar.make(capturedImageView, "Error al subir la imagen.", Snackbar.LENGTH_LONG).show();
        });
    }

    private void saveToFirestore(String imageUrl, String wineName, String variety, String vintage, String origin, String percentage) {
        CollectionReference userCollection = firestore.collection("descriptions").document(userId).collection("wineDescriptions");

        Map<String, Object> imageData = new HashMap<>();
        imageData.put("imageUrl", imageUrl);
        imageData.put("recognizedText", recognizedText);
        imageData.put("wineName", wineName);
        imageData.put("variety", variety);
        imageData.put("vintage", vintage);
        imageData.put("origin", origin);
        imageData.put("percentage", percentage);

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
