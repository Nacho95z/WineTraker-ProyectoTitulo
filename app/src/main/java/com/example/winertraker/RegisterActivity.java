package com.example.winertraker;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    // Declaración de variables para Firebase Auth y UI
    private FirebaseAuth mAuth;
    private EditText editTextEmail, editTextPassword;
    private Button buttonRegister, buttonBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Referenciar elementos de la UI
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonRegister = findViewById(R.id.buttonRegister);
        buttonBack = findViewById(R.id.buttonBack);

        // Configurar el botón de registro
        buttonRegister.setOnClickListener(view -> registerUser());

        // Configurar el botón de retroceso
        buttonBack.setOnClickListener(view -> finish()); // Cierra la actividad y regresa a la anterior
    }

    private void registerUser() {
        // Mensaje para verificar que el método es llamado
        showToast("Registrando usuario...");

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showToast("Por favor, ingresa el correo y la contraseña.");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        showToast("Registro exitoso, bienvenido!");
                        goToHome();
                    } else {
                        showToast("Error en el registro: " + task.getException().getMessage());
                    }
                });
    }

    private void goToHome() {
        // Redirigir a HomeActivity
        Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
        startActivity(intent);
        finish(); // Cierra RegisterActivity para que no se pueda volver a esta
    }

    private void showToast(String message) {
        // Mostrar un mensaje en pantalla
        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
