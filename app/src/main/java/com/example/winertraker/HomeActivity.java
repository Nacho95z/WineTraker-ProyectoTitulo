package com.example.winertraker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class HomeActivity extends AppCompatActivity {
    private FirebaseUser user;
    private String userId;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private TextView emailTextView, providerTextView, emailVerifiedTextView, uidTextView;
    private Button logoutButton, captureButton, addBottleButton, viewCollectionButton;
    private StorageReference storageRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Storage reference
        storageRef = FirebaseStorage.getInstance().getReference();

        // Initialize Firebase Auth
        user = FirebaseAuth.getInstance().getCurrentUser();
        userId = user != null ? user.getUid() : "unknown";

        // Edge-to-Edge configuration
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        emailTextView = findViewById(R.id.emailTextView);
        providerTextView = findViewById(R.id.providerTextView);
        emailVerifiedTextView = findViewById(R.id.emailVerifiedTextView);
        uidTextView = findViewById(R.id.uidTextView);
        logoutButton = findViewById(R.id.logoutButton);
        addBottleButton = findViewById(R.id.addBottleButton);
        viewCollectionButton = findViewById(R.id.viewCollectionButton);

        // Display user information
        if (user != null) {
            String email = user.getEmail();
            boolean emailVerified = user.isEmailVerified();
            String uid = user.getUid();

            emailTextView.setText("Email: " + email);
            emailVerifiedTextView.setText("Email Verified: " + emailVerified);
            uidTextView.setText("UID: " + uid);

            if (user.getDisplayName() != null) {
                providerTextView.setText("Name: " + user.getDisplayName());
            } else {
                providerTextView.setText("Name: Not Available");
            }
        } else {
            // Redirect to AuthActivity if no user is authenticated
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }

        // Configure logout button
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(HomeActivity.this, AuthActivity.class));
            finish();
        });

        // Redirect to CaptureIMG activity when addBottleButton is clicked
        addBottleButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CaptureIMG.class);
            startActivity(intent);
        });

        // Redirect to ViewCollectionActivity when viewCollectionButton is clicked
        viewCollectionButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ViewCollectionActivity.class);
            startActivity(intent);
        });
    }
}
