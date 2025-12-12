package com.example.winertraker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseUser user;
    private SharedPreferences prefs;

    // Perfil
    private TextView txtProfileName, txtProfileEmail, txtProfileUid;
    private Button btnEditName, btnEditEmail;

    // Seguridad
    private Button btnChangePassword;

    // Sesión
    private SwitchCompat switchRememberSession;

    // Huella
    private SwitchCompat switchBiometricGate;

    // Notificaciones
    private SwitchCompat switchNotifOptimal, switchNotifExpiry, switchNotifNews;

    // Tema
    private RadioGroup rgTheme;
    private RadioButton rbThemeLight, rbThemeDark, rbThemeSystem;

    // Respaldo
    private Button btnBackup, btnRestore, btnExport;

    // Acerca de
    private TextView txtAboutVersion, txtPrivacyPolicy, txtTerms;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        user = FirebaseAuth.getInstance().getCurrentUser();
        prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);

        initViews();
        loadUserInfo();
        loadPreferences();
        setupListeners();
    }

    private void initViews() {
        // Perfil
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        txtProfileUid = findViewById(R.id.txtProfileUid);
        btnEditName = findViewById(R.id.btnEditName);
        btnEditEmail = findViewById(R.id.btnEditEmail);
        switchBiometricGate = findViewById(R.id.switchBiometricGate);


        // Seguridad
        btnChangePassword = findViewById(R.id.btnChangePassword);

        // Sesión
        switchRememberSession = findViewById(R.id.switchRememberSession);

        // Notificaciones
        switchNotifOptimal = findViewById(R.id.switchNotifOptimal);
        switchNotifExpiry = findViewById(R.id.switchNotifExpiry);
        switchNotifNews = findViewById(R.id.switchNotifNews);

        // Tema
        rgTheme = findViewById(R.id.rgTheme);
        rbThemeLight = findViewById(R.id.rbThemeLight);
        rbThemeDark = findViewById(R.id.rbThemeDark);
        rbThemeSystem = findViewById(R.id.rbThemeSystem);

        // Respaldo
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnExport = findViewById(R.id.btnExport);

        // Acerca de
        txtAboutVersion = findViewById(R.id.txtAboutVersion);
        txtPrivacyPolicy = findViewById(R.id.txtPrivacyPolicy);
        txtTerms = findViewById(R.id.txtTerms);
    }

    private void loadUserInfo() {
        if (user == null) return;

        String name = user.getDisplayName();
        if (name == null || name.isEmpty()) {
            name = "Amante del vino";
        }
        txtProfileName.setText(name);
        txtProfileEmail.setText(user.getEmail());
        txtProfileUid.setText("UID: " + user.getUid());

        // Versión (puedes traerla del BuildConfig.VERSION_NAME)
        txtAboutVersion.setText("Versión " + BuildConfig.VERSION_NAME);
    }

    private void loadPreferences() {
        boolean remember = prefs.getBoolean("remember_session", true);
        switchRememberSession.setChecked(remember);

        switchNotifOptimal.setChecked(prefs.getBoolean("notif_optimal", true));
        switchNotifExpiry.setChecked(prefs.getBoolean("notif_expiry", true));
        switchNotifNews.setChecked(prefs.getBoolean("notif_news", true));
        boolean biometricEnabled = prefs.getBoolean("biometric_gate_enabled", true);
        switchBiometricGate.setChecked(biometricEnabled);


        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) {
            rbThemeLight.setChecked(true);
        } else if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) {
            rbThemeDark.setChecked(true);
        } else {
            rbThemeSystem.setChecked(true);
        }
    }

    private void setupListeners() {

        // Cambiar contraseña: enviar mail de reset
        btnChangePassword.setOnClickListener(v -> {
            if (user != null && user.getEmail() != null) {
                FirebaseAuth.getInstance()
                        .sendPasswordResetEmail(user.getEmail())
                        .addOnSuccessListener(unused ->
                                Toast.makeText(this, "Te enviamos un correo para cambiar la contraseña", Toast.LENGTH_LONG).show()
                        )
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Error al enviar correo: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
            }
        });

        switchBiometricGate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("biometric_gate_enabled", isChecked).apply();
        });


        // Recordar sesión
        switchRememberSession.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("remember_session", isChecked).apply();

            if (!isChecked) {
                prefs.edit().putBoolean("biometric_gate_enabled", false).apply();
                switchBiometricGate.setChecked(false);
            }
        });


        // Notificaciones
        switchNotifOptimal.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_optimal", isChecked).apply());

        switchNotifExpiry.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_expiry", isChecked).apply());

        switchNotifNews.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_news", isChecked).apply());

        // Tema
        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rbThemeLight) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rbThemeDark) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
            prefs.edit().putInt("theme_mode", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        // Acerca de: enlaces (puedes cambiar las URLs después)
        txtPrivacyPolicy.setOnClickListener(v -> openUrl("https://tusitio.cl/politica-privacidad"));
        txtTerms.setOnClickListener(v -> openUrl("https://tusitio.cl/terminos-uso"));

        // Perfil: por ahora solo muestra toasts (luego podemos implementar diálogos reales)
        btnEditName.setOnClickListener(v ->
                Toast.makeText(this, "Cambiar nombre (por implementar)", Toast.LENGTH_SHORT).show());

        btnEditEmail.setOnClickListener(v ->
                Toast.makeText(this, "Cambiar correo (por implementar)", Toast.LENGTH_SHORT).show());



        // Respaldo: placeholders por ahora
        btnBackup.setOnClickListener(v ->
                Toast.makeText(this, "Backup en Firestore (por implementar)", Toast.LENGTH_SHORT).show());

        btnRestore.setOnClickListener(v ->
                Toast.makeText(this, "Restaurar backup (por implementar)", Toast.LENGTH_SHORT).show());

        btnExport.setOnClickListener(v ->
                Toast.makeText(this, "Exportar colección a CSV/PDF (por implementar)", Toast.LENGTH_SHORT).show());
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}
