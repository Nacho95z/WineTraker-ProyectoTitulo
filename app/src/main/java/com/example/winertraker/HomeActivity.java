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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {
    private FirebaseUser user;
    private TextView emailTextView, providerTextView, emailVerifiedTextView, uidTextView;
    private Button logoutButton, addBottleButton, viewCollectionButton;

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

        if (user == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        // Initialize views
        emailTextView = findViewById(R.id.emailTextView);
        providerTextView = findViewById(R.id.providerTextView);
        emailVerifiedTextView = findViewById(R.id.emailVerifiedTextView);
        uidTextView = findViewById(R.id.uidTextView);
        logoutButton = findViewById(R.id.logoutButton);
        addBottleButton = findViewById(R.id.addBottleButton);
        viewCollectionButton = findViewById(R.id.viewCollectionButton);

        // Display user information
        emailTextView.setText("Email: " + user.getEmail());
        emailVerifiedTextView.setText("Email Verified: " + user.isEmailVerified());
        uidTextView.setText("UID: " + user.getUid());

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
    }

    // Helper method to navigate to another activity
    private void redirectToActivity(Class<?> activityClass) {
        Intent intent = new Intent(HomeActivity.this, activityClass);
        startActivity(intent);
    }
}
