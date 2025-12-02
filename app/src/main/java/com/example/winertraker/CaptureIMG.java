package com.example.winertraker;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
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
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.exifinterface.media.ExifInterface;   // <-- NUEVO

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
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

    // Drawer
    private DrawerLayout drawerLayoutCapture;
    private NavigationView navigationView;
    private ImageView menuIcon;

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

        drawerLayoutCapture = findViewById(R.id.drawerLayoutCapture);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        menuIcon.setOnClickListener(v ->
                drawerLayoutCapture.openDrawer(GravityCompat.START)
        );

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                Intent intent = new Intent(CaptureIMG.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_my_cellar) {
                Intent intent = new Intent(CaptureIMG.this, ViewCollectionActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(CaptureIMG.this, SettingsActivity.class));

            } else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();
                getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("remember_session", false)
                        .apply();

                Intent intent = new Intent(CaptureIMG.this, AuthActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }

            drawerLayoutCapture.closeDrawer(GravityCompat.START);
            return true;
        });

        previewView = findViewById(R.id.viewFinder);
        FloatingActionButton captureButton = findViewById(R.id.image_capture_button);

        previewView.post(this::startCamera);

        captureButton.setOnClickListener(v -> captureImage());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

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

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                        // 🔄 Corregimos orientación ANTES de seguir
                        fixImageOrientation(photoFile);

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
                }
        );
    }

    /**
     * Lee el EXIF del archivo y rota la imagen si es necesario,
     * sobrescribiendo el mismo JPG.
     */
    private void fixImageOrientation(File photoFile) {
        try {
            String path = photoFile.getAbsolutePath();
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            int rotationDegrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationDegrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationDegrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationDegrees = 270;
                    break;
                default:
                    rotationDegrees = 0;
            }

            if (rotationDegrees == 0) {
                return; // ya está bien orientada
            }

            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) return;

            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
            );
            bitmap.recycle();

            // sobrescribimos el archivo
            FileOutputStream out = new FileOutputStream(photoFile);
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            rotated.recycle();

            // dejamos la orientación en normal
            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processImageForText(Uri uri, ProgressDialog progressDialog) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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

    @Override
    public void onBackPressed() {
        if (drawerLayoutCapture != null &&
                drawerLayoutCapture.isDrawerOpen(GravityCompat.START)) {
            drawerLayoutCapture.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}