package com.example.winertraker;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(StartActivity.this, AuthActivity.class));
            finish();
        }, 5000);
    }
}