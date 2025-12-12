package com.example.winertraker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class StartActivity extends AppCompatActivity {

    private static final long SPLASH_TIME = 1500; // ⏱ 2 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        new Handler(Looper.getMainLooper()).postDelayed(this::decideNextScreen, SPLASH_TIME);
    }

    private void decideNextScreen() {
        SharedPreferences prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);

        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);
        boolean remember = prefs.getBoolean("remember_session", true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // 1️⃣ No aceptó términos → Auth (para mostrar dialog)
        if (!termsAccepted) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        // 2️⃣ Sesión recordada → Huella → Home
        if (user != null && remember) {
            startActivity(new Intent(this, BiometricGateActivity.class));
            finish();
            return;
        }

        // 3️⃣ Caso normal → Login
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }
}
