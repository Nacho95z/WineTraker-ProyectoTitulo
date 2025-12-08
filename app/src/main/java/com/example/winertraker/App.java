package com.example.winertraker;

import android.app.Application;

import com.example.winertraker.BuildConfig;
import com.example.winertraker.PicassoClient;
import com.squareup.picasso.Picasso;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Obtienes tu instancia de PicassoClient
        Picasso picasso = PicassoClient.getInstance(this);

        if (BuildConfig.DEBUG) {
            // 🔍 Muestra triángulos de colores en las imágenes
            picasso.setIndicatorsEnabled(true);
            // Opcional: logs extra en Logcat
            picasso.setLoggingEnabled(true);
        }
    }
}
