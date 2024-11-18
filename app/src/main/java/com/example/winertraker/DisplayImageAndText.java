// DisplayImageAndText.java
package com.example.winertraker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class DisplayImageAndText extends AppCompatActivity {

    private ImageView capturedImageView;
    private TextView recognizedTextView;
    private Uri imageUri;
    private String recognizedText;
    private StorageReference storageRef;
    private String userId = "user_id"; // Reemplaza por el id de usuario correspondiente

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_image_and_text);

        capturedImageView = findViewById(R.id.capturedImageView);
        recognizedTextView = findViewById(R.id.recognizedTextView);
        Button saveButton = findViewById(R.id.buttonSave);
        Button discardButton = findViewById(R.id.buttonDiscard);

        // Initialize Firebase Storage reference
        storageRef = FirebaseStorage.getInstance().getReference();

        // Obtener los datos pasados desde la actividad anterior
        imageUri = getIntent().getParcelableExtra("imageUri");
        recognizedText = getIntent().getStringExtra("recognizedText");

        if (imageUri != null) {
            capturedImageView.setImageURI(imageUri);
        }
        if (recognizedText != null) {
            recognizedTextView.setText(recognizedText);
        }

        // Acción para guardar la imagen en Firebase
        saveButton.setOnClickListener(v -> saveImageToFirebase());

        // Acción para descartar la imagen y volver a tomar otra
        discardButton.setOnClickListener(v -> {
            Intent intent = new Intent(DisplayImageAndText.this, CaptureIMG.class);
            startActivity(intent);
            finish();
        });
    }

    private void saveImageToFirebase() {
        if (imageUri == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageReference fileRef = storageRef.child("images/" + imageUri.getLastPathSegment());

        // Add metadata
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setCustomMetadata("userId", userId)
                .build();

        UploadTask uploadTask = fileRef.putFile(imageUri, metadata);

        uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            String downloadUrl = uri.toString();
            Toast.makeText(DisplayImageAndText.this, "Image saved to Firebase: " + downloadUrl, Toast.LENGTH_SHORT).show();
        })).addOnFailureListener(e -> {
            Toast.makeText(DisplayImageAndText.this, "Failed to save image", Toast.LENGTH_SHORT).show();
        });
    }
}
