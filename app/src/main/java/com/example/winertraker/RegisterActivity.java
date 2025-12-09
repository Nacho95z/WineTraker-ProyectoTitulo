package com.example.winertraker;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Campos UI
    private TextInputEditText etName, etSurname, etBirthDate, etEmail, etPassword, etConfirmPassword;
    private RadioGroup rgGender;
    private Button btnRegister;

    // Checklist UI
    private TextView reqMinChar, reqUpper, reqLower, reqNumber, reqSpecial;

    // Estados de validación
    private boolean isPasswordValid = false;
    private boolean isAgeValid = false; // 🆕 Variable para controlar la edad

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupDatePicker();      // 📅 Aquí está la lógica de edad
        setupPasswordWatcher(); // 🔒 Aquí está la lógica de contraseña

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etSurname = findViewById(R.id.etSurname);
        etBirthDate = findViewById(R.id.etBirthDate);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        rgGender = findViewById(R.id.rgGender);
        btnRegister = findViewById(R.id.btnRegister);

        // Checklist
        reqMinChar = findViewById(R.id.reqMinChar);
        reqUpper = findViewById(R.id.reqUpper);
        reqLower = findViewById(R.id.reqLower);
        reqNumber = findViewById(R.id.reqNumber);
        reqSpecial = findViewById(R.id.reqSpecial);
    }

    // -----------------------------------------------------------------------
    // 📅 LÓGICA DE FECHA Y VALIDACIÓN DE +18 AÑOS
    // -----------------------------------------------------------------------
    private void setupDatePicker() {
        etBirthDate.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year1, monthOfYear, dayOfMonth) -> {
                        // 1. Mostrar la fecha seleccionada
                        String selectedDate = dayOfMonth + "/" + (monthOfYear + 1) + "/" + year1;
                        etBirthDate.setText(selectedDate);

                        // 2. VALIDAR EDAD
                        validateAge(year1, monthOfYear, dayOfMonth);

                    }, year, month, day);

            // Opcional: Limitar el calendario para no permitir fechas futuras
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

            datePickerDialog.show();
        });
    }

    private void validateAge(int year, int month, int day) {
        Calendar userBirthDate = Calendar.getInstance();
        userBirthDate.set(year, month, day);

        // Calcular la fecha exacta de hace 18 años
        Calendar eighteenYearsAgo = Calendar.getInstance();
        eighteenYearsAgo.add(Calendar.YEAR, -18);

        // Comparar: Si la fecha de nacimiento es DESPUÉS de hace 18 años, es menor de edad.
        if (userBirthDate.after(eighteenYearsAgo)) {
            // ❌ ES MENOR DE EDAD
            etBirthDate.setError("Debes ser mayor de 18 años para registrarte");
            isAgeValid = false;
            Toast.makeText(this, "Lo sentimos, debes ser mayor de 18 años.", Toast.LENGTH_SHORT).show();
        } else {
            // ✅ ES MAYOR DE EDAD
            etBirthDate.setError(null); // Quita el error rojo
            isAgeValid = true;
        }
    }

    // -----------------------------------------------------------------------
    // 🔒 LÓGICA DE CONTRASEÑA (Checklist)
    // -----------------------------------------------------------------------
    private void setupPasswordWatcher() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePasswordRules(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void validatePasswordRules(String password) {
        boolean hasMinChar = password.length() >= 6;
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasNumber = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find();

        updateRequirementView(reqMinChar, hasMinChar);
        updateRequirementView(reqUpper, hasUpper);
        updateRequirementView(reqLower, hasLower);
        updateRequirementView(reqNumber, hasNumber);
        updateRequirementView(reqSpecial, hasSpecial);

        isPasswordValid = hasMinChar && hasUpper && hasLower && hasNumber && hasSpecial;
    }

    private void updateRequirementView(TextView view, boolean isValid) {
        if (isValid) {
            view.setTextColor(Color.parseColor("#2E7D32")); // Verde
            view.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.checkbox_on_background, 0, 0, 0);
        } else {
            view.setTextColor(Color.BLACK); // Negro legible
            view.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.checkbox_off_background, 0, 0, 0);
        }
    }

    // -----------------------------------------------------------------------
    // 🚀 PROCESO DE REGISTRO
    // -----------------------------------------------------------------------
    private void registerUser() {
        String name = etName.getText().toString().trim();
        String surname = etSurname.getText().toString().trim();
        String birthDate = etBirthDate.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        String gender = "";
        int selectedId = rgGender.getCheckedRadioButtonId();
        if (selectedId == R.id.rbMale) gender = "Masculino";
        else if (selectedId == R.id.rbFemale) gender = "Femenino";
        else if (selectedId == R.id.rbOther) gender = "Otro";

        // --- VALIDACIONES ---
        if (name.isEmpty()) { etName.setError("Falta nombre"); return; }
        if (surname.isEmpty()) { etSurname.setError("Falta apellido"); return; }
        if (birthDate.isEmpty()) { etBirthDate.setError("Selecciona fecha"); return; }

        // 🆕 Validar Edad (+18)
        if (!isAgeValid) {
            etBirthDate.setError("Debes ser mayor de 18 años");
            Toast.makeText(this, "Fecha de nacimiento no válida (Menor de 18).", Toast.LENGTH_SHORT).show();
            return;
        }

        if (gender.isEmpty()) { Toast.makeText(this, "Selecciona tu sexo", Toast.LENGTH_SHORT).show(); return; }
        if (email.isEmpty()) { etEmail.setError("Falta correo"); return; }

        // Validar Contraseña
        if (!isPasswordValid) {
            etPassword.setError("La contraseña no cumple los requisitos");
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Las contraseñas no coinciden");
            etConfirmPassword.requestFocus();
            return;
        }

        // --- INICIO DE REGISTRO ---
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Creando cuenta y enviando verificación...");
        pd.setCancelable(false);
        pd.show();

        String finalGender = gender;

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        String uid = user.getUid();

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("uid", uid);
                        userData.put("name", name);
                        userData.put("surname", surname);
                        userData.put("birthDate", birthDate);
                        userData.put("gender", finalGender);
                        userData.put("email", email);

                        // Guardar en Firestore
                        db.collection("users").document(uid).set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    // Actualizar nombre en Auth
                                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                            .setDisplayName(name + " " + surname)
                                            .build();
                                    user.updateProfile(profileUpdates);

                                    // Enviar correo de verificación
                                    user.sendEmailVerification()
                                            .addOnCompleteListener(verifyTask -> {
                                                pd.dismiss();
                                                if (verifyTask.isSuccessful()) {
                                                    Toast.makeText(RegisterActivity.this,
                                                            "Cuenta creada. ¡REVISA TU CORREO para activar la cuenta!",
                                                            Toast.LENGTH_LONG).show();

                                                    // Cerrar sesión y volver
                                                    mAuth.signOut();
                                                    finish();
                                                } else {
                                                    Toast.makeText(RegisterActivity.this,
                                                            "Error enviando correo: " + verifyTask.getException().getMessage(),
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    pd.dismiss();
                                    Toast.makeText(RegisterActivity.this, "Error guardando datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });

                    } else {
                        pd.dismiss();
                        Toast.makeText(RegisterActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}