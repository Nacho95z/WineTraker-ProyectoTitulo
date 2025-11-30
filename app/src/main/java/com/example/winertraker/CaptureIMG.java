package com.example.winertraker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface; // Importante para leer la rotación
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class CaptureIMG extends AppCompatActivity {
    private FirebaseUser user;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private StorageReference storageRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_img);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        storageRef = FirebaseStorage.getInstance().getReference();
        user = FirebaseAuth.getInstance().getCurrentUser();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.viewFinder);
        FloatingActionButton captureButton = findViewById(R.id.image_capture_button);
        ImageButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());
        previewView.post(this::startCamera);
        captureButton.setOnClickListener(v -> captureImage());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Toast.makeText(CaptureIMG.this, "Error al iniciar cámara", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageCapture = new ImageCapture.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Toast.makeText(this, "Fallo al vincular cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private void captureImage() {
        if (imageCapture == null) return;

        String uniqueFileName = System.currentTimeMillis() + ".jpg";
        File photoFile = new File(getExternalFilesDir(null), uniqueFileName);

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // --- SOLUCIÓN DEFINITIVA DE ROTACIÓN ---
                // Corregimos la imagen físicamente antes de procesarla
                fixImageRotation(photoFile);
                // ---------------------------------------

                Uri fileUri = Uri.fromFile(photoFile);

                ProgressDialog progressDialog = new ProgressDialog(CaptureIMG.this);
                progressDialog.setMessage("Procesando imagen...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                processImageForText(fileUri, progressDialog);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CaptureIMG.this, "Error al capturar foto", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- MÉTODOS NUEVOS PARA CORREGIR ROTACIÓN ---
    private void fixImageRotation(File photoFile) {
        try {
            // Leemos la etiqueta de rotación que puso la cámara
            ExifInterface ei = new ExifInterface(photoFile.getAbsolutePath());
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            Bitmap rotatedBitmap = null;

            // Rotamos según lo que diga la etiqueta
            switch(orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotatedBitmap = rotateImage(bitmap, 90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotatedBitmap = rotateImage(bitmap, 180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotatedBitmap = rotateImage(bitmap, 270);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotatedBitmap = bitmap;
            }

            // Si hubo rotación, sobrescribimos el archivo con la imagen correcta
            if (rotatedBitmap != bitmap) {
                try (FileOutputStream out = new FileOutputStream(photoFile)) {
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
    // ---------------------------------------------

    private void processImageForText(Uri uri, ProgressDialog progressDialog) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        progressDialog.dismiss();
                        String recognizedText = text.getText();
                        showImageAndText(uri, recognizedText);
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(CaptureIMG.this, "Error al procesar texto", Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            e.printStackTrace();
            progressDialog.dismiss();
        }
    }

    private void showImageAndText(Uri imageUri, String recognizedText) {
        Intent intent = new Intent(this, DisplayImageAndText.class);
        intent.putExtra("imageUri", imageUri);
        intent.putExtra("recognizedText", recognizedText);
        startActivity(intent);
    }
}