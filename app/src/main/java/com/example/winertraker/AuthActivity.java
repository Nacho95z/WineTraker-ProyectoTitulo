package com.example.winertraker;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
    private static final String TAG = "AuthActivity";

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private Button buttonLogin, buttonRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_auth);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        buttonLogin = findViewById(R.id.loginButton);
        buttonRegister = findViewById(R.id.registerButton);

        // 🔹 El botón ahora abre el diálogo de login con correo
        buttonLogin.setOnClickListener(view -> showEmailLoginDialog());

        buttonRegister.setOnClickListener(view -> {
            Intent intent = new Intent(AuthActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // ---- GOOGLE SIGN-IN ----
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.signIn).setOnClickListener(view -> signInWithGoogle());
    }

    // 🔹 DIÁLOGO PARA LOGIN CON CORREO
    private void showEmailLoginDialog() {

        // Creamos un Dialog con tu tema sin fondo gris
        Dialog dialog = new Dialog(this, R.style.WineDialogTheme);
        dialog.setContentView(R.layout.dialog_email_login);
        dialog.setCanceledOnTouchOutside(false); // opcional: que no se cierre tocando fuera

        // Referencias a las vistas dentro del layout del diálogo
        EditText dialogEmailEditText = dialog.findViewById(R.id.dialogEmailEditText);
        EditText dialogPasswordEditText = dialog.findViewById(R.id.dialogPasswordEditText);
        CheckBox dialogShowPasswordCheckBox = dialog.findViewById(R.id.dialogShowPasswordCheckBox);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelar);
        Button btnIngresar = dialog.findViewById(R.id.btnIngresar);

        // 🔸 Mostrar/Ocultar contraseña
        dialogShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                dialogPasswordEditText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                dialogPasswordEditText.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                );
            }
            dialogPasswordEditText.setSelection(dialogPasswordEditText.getText().length());
        });

        // 🔸 Botón CANCELAR
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        // 🔸 Botón INGRESAR con validación
        btnIngresar.setOnClickListener(v -> {
            String email = dialogEmailEditText.getText().toString().trim();
            String password = dialogPasswordEditText.getText().toString().trim();

            if (email.isEmpty()) {
                dialogEmailEditText.setError("Ingrese un correo.");
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                dialogEmailEditText.setError("Correo inválido.");
                return;
            }

            if (password.isEmpty()) {
                dialogPasswordEditText.setError("Ingrese una contraseña.");
                return;
            }

            if (password.length() < 6) {
                dialogPasswordEditText.setError("Mínimo 6 caracteres.");
                return;
            }

            // Si todo está OK:
            dialog.dismiss();
            loginUser(email, password);
        });

        // Mostrar el diálogo
        dialog.show();
    }

    // 🔹 LOGIN A FIREBASE
    private void loginUser(String email, String password) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Iniciando sesión...");
        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Intent intent = new Intent(AuthActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Error desconocido.";
                        Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---- GOOGLE SIGN-IN ----
    private void signInWithGoogle() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Cargando...");
        progressDialog.show();

        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);

        progressDialog.dismiss();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Autenticando...");
            progressDialog.show();

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account, progressDialog);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error Google: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account, ProgressDialog progressDialog) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        startActivity(new Intent(AuthActivity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Error: " + (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}