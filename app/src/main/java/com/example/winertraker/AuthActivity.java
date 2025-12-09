package com.example.winertraker;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private Button buttonLogin, buttonRegister;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_auth);

        prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        buttonLogin = findViewById(R.id.loginButton);
        buttonRegister = findViewById(R.id.registerButton);

        buttonLogin.setOnClickListener(view -> showEmailLoginDialog());
        buttonRegister.setOnClickListener(view -> {
            Intent intent = new Intent(AuthActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.signIn).setOnClickListener(view -> signInWithGoogle());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Lógica de inicio: Términos -> Sesión
        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);

        if (!termsAccepted) {
            showTermsDialog();
        } else {
            checkSessionAndRedirect();
        }
    }

    private void checkSessionAndRedirect() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean remember = prefs.getBoolean("remember_session", true);

        // Solo redirigir si hay usuario, quiere recordar Y EL CORREO ESTÁ VERIFICADO
        if (currentUser != null && remember && currentUser.isEmailVerified()) {
            Intent intent = new Intent(AuthActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }
    }

    // --- DIÁLOGO DE TÉRMINOS Y CONDICIONES ---
    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_termconditions); // Nombre de tu XML de términos
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTermsContent = dialog.findViewById(R.id.tvTermsText);
        CheckBox checkAccept = dialog.findViewById(R.id.checkAccept);
        Button btnAccept = dialog.findViewById(R.id.btnAcceptTerms);

        String technicalTerms =
                "MARCO NORMATIVO Y TÉRMINOS DE USO\n\n" +
                        "1. CUMPLIMIENTO LEGAL (CHILE)\n" +
                        "Conforme al Art. 42 de la Ley N° 19.925 (Ley de Alcoholes), el acceso a esta aplicación está estrictamente restringido a mayores de 18 años.\n\n" +
                        "2. PRIVACIDAD Y SEGURIDAD DE DATOS\n" +
                        "El tratamiento de datos se rige por la Ley N° 19.628. La arquitectura sigue lineamientos ISO/IEC 27001.\n\n" +
                        "3. CALIDAD DE SOFTWARE E IA\n" +
                        "Prototipo académico desarrollado bajo criterios ISO/IEC 25010. La IA tiene fines referenciales.\n\n" +
                        "4. DECLARACIÓN JURADA\n" +
                        "Al aceptar, usted declara ser mayor de edad y responsable del uso de la información.";

        if (tvTermsContent != null) tvTermsContent.setText(technicalTerms);

        btnAccept.setEnabled(false);
        btnAccept.setAlpha(0.5f);

        checkAccept.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnAccept.setEnabled(isChecked);
            btnAccept.setAlpha(isChecked ? 1.0f : 0.5f);
        });

        btnAccept.setOnClickListener(v -> {
            prefs.edit().putBoolean("terms_accepted", true).apply();
            dialog.dismiss();
            Toast.makeText(AuthActivity.this, "Términos aceptados.", Toast.LENGTH_SHORT).show();
            // No redirigimos, se queda en Login
        });

        dialog.show();
    }

    // --- LOGIN CON CORREO (CON VALIDACIÓN DE VERIFICACIÓN) ---
    private void showEmailLoginDialog() {
        Dialog dialog = new Dialog(this, R.style.WineDialogTheme);
        dialog.setContentView(R.layout.dialog_email_login);
        dialog.setCanceledOnTouchOutside(false);

        EditText dialogEmailEditText = dialog.findViewById(R.id.dialogEmailEditText);
        EditText dialogPasswordEditText = dialog.findViewById(R.id.dialogPasswordEditText);
        CheckBox dialogShowPasswordCheckBox = dialog.findViewById(R.id.dialogShowPasswordCheckBox);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelar);
        Button btnIngresar = dialog.findViewById(R.id.btnIngresar);

        dialogShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                dialogPasswordEditText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                dialogPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            dialogPasswordEditText.setSelection(dialogPasswordEditText.getText().length());
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnIngresar.setOnClickListener(v -> {
            String email = dialogEmailEditText.getText().toString().trim();
            String password = dialogPasswordEditText.getText().toString().trim();

            if (email.isEmpty()) { dialogEmailEditText.setError("Falta correo"); return; }
            if (password.isEmpty()) { dialogPasswordEditText.setError("Falta contraseña"); return; }

            dialog.dismiss();
            loginUser(email, password);
        });

        dialog.show();
    }

    private void loginUser(String email, String password) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Iniciando sesión...");
        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        // 🔒 CANDADO DE SEGURIDAD: ¿CORREO VERIFICADO? 🔒
                        if (user != null && user.isEmailVerified()) {
                            // SI: Entra
                            prefs.edit().putBoolean("remember_session", true).apply();
                            Intent intent = new Intent(AuthActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // NO: Fuera
                            Toast.makeText(AuthActivity.this,
                                    "Cuenta no activada. Por favor verifica el enlace enviado a tu correo.",
                                    Toast.LENGTH_LONG).show();
                            mAuth.signOut(); // Cerramos la sesión temporal
                        }

                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Error.";
                        Toast.makeText(this, "Error: " + err, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- GOOGLE LOGIN ---
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error Google: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Google ya verifica correos, así que dejamos pasar
                        prefs.edit().putBoolean("remember_session", true).apply();
                        startActivity(new Intent(AuthActivity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Error autenticación.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}