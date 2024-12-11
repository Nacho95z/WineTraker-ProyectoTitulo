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
import androidx.recyclerview.widget.DiffUtil;

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

        // Display image and text
        if (imageUri != null) {
            capturedImageView.setImageURI(imageUri);
        }
        if (recognizedText != null) {
            recognizedTextEdit.setText(recognizedText);
            autofillFields(recognizedText);
        }

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
        if (recognizedText.isEmpty() || wineName.isEmpty() || variety.isEmpty() || vintage.isEmpty() || origin.isEmpty() || percentage.isEmpty()) {
            Toast.makeText(this, "Por favor complete todos los campos requeridos.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!vintage.matches("\\d{4}")) {
            Toast.makeText(this, "Ingrese un año válido (4 dígitos).", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!percentage.matches("\\d+(\\.\\d+)?") || Double.parseDouble(percentage) > 100) {
            Toast.makeText(this, "Ingrese un porcentaje válido entre 0 y 100.", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
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
