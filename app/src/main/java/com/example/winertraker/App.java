package com.example.winertraker;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.winertraker.BuildConfig;
import com.example.winertraker.PicassoClient;
import com.squareup.picasso.Picasso;

public class App extends Application {

    // 🔔 Estado de notificación (solo una vez por apertura de la app)
    private boolean optimalNotificationSent = false;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences p = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);
        int mode = p.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);

        // Obtienes tu instancia de PicassoClient
        Picasso picasso = PicassoClient.getInstance(this);

        if (BuildConfig.DEBUG) {
            // 🔍 Muestra triángulos de colores en las imágenes
            picasso.setIndicatorsEnabled(true);
            // Opcional: logs extra en Logcat
            picasso.setLoggingEnabled(true);
        }
    }

    // ===== Control de notificación de consumo óptimo =====

    public boolean isOptimalNotificationSent() {
        return optimalNotificationSent;
    }

    public void setOptimalNotificationSent(boolean sent) {
        this.optimalNotificationSent = sent;
    }

    public void resetOptimalNotification() {
        this.optimalNotificationSent = false;
    }
}
