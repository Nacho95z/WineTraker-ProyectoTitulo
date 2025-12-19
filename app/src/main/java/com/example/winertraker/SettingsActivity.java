package com.example.winertraker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class SettingsActivity extends AppCompatActivity {

    private FirebaseUser user;
    private SharedPreferences prefs;

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuIcon;

    // Header drawer (si usas nav_header)
    private TextView headerTitle, headerEmail;

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

    // Firestore
    private FirebaseFirestore db;

    // Launchers
    private ActivityResultLauncher<String> createCsvLauncher;
    private ActivityResultLauncher<String> createPdfLauncher;

    // Estilo WineTrack
    private static final int COLOR_WINE = Color.parseColor("#B22034");
    private static final int COLOR_GRAY = Color.parseColor("#555555");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyThemeFromPrefs();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        user = FirebaseAuth.getInstance().getCurrentUser();
        prefs = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();

        // ✅ Drawer (misma lógica que Home)
        initializeDrawerViews();
        setupDrawerUserInfo();
        setupDrawerActions();

        // CSV launcher
        createCsvLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> {
                    if (uri == null) return;
                    exportCollectionToCsv(uri);
                }
        );

        // PDF launcher
        createPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> {
                    if (uri == null) return;
                    exportCollectionToPdfResumenYFichas(uri); // PDF estilo ficha
                }
        );

        initViews();
        loadUserInfo();
        loadPreferences();
        setupListeners();
    }

    // =========================================================
// DRAWER (igual que Home)
// =========================================================
    private void initializeDrawerViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        menuIcon = findViewById(R.id.menuIcon);

        if (navigationView != null) {
            android.view.View headerView = navigationView.getHeaderView(0);
            headerTitle = headerView.findViewById(R.id.headerTitle);
            headerEmail = headerView.findViewById(R.id.headerEmail);
        }
    }

    private void setupDrawerUserInfo() {
        if (user != null && headerTitle != null && headerEmail != null) {
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) displayName = "Amante del vino";
            headerTitle.setText(displayName);
            headerEmail.setText(user.getEmail());
        }
    }

    private void setupDrawerActions() {
        if (menuIcon != null && drawerLayout != null) {
            menuIcon.setOnClickListener(v ->
                    drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
            );
        }

        if (navigationView != null && drawerLayout != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    redirectToActivity(HomeActivity.class);

                } else if (id == R.id.nav_my_cellar) {
                    redirectToActivity(ViewCollectionActivity.class);

                } else if (id == R.id.nav_consumed) {
                    redirectToActivity(ConsumedWinesActivity.class);

                } else if (id == R.id.nav_settings) {
                    // ✅ ya estás aquí
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    return true;

                } else if (id == R.id.nav_logout) {
                    performLogout();
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    return true;
                }

                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                return true;
            });
        }
    }

    private void redirectToActivity(Class<?> cls) {
        Intent intent = new Intent(SettingsActivity.this, cls);
        startActivity(intent);
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("wtrack_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("remember_session", false)
                .apply();

        Intent intent = new Intent(SettingsActivity.this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void applyThemeFromPrefs() {
        SharedPreferences p = getSharedPreferences("wtrack_prefs", MODE_PRIVATE);
        int mode = p.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
    // =========================================================
    // EXPORT CSV
    // =========================================================
    private void exportCollectionToCsv(Uri uri) {
        if (user == null) {
            Toast.makeText(this, "Debes iniciar sesión para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Generando archivo Excel (CSV)...", Toast.LENGTH_SHORT).show();

        db.collection("descriptions")
                .document(user.getUid())
                .collection("wineDescriptions")
                .get()
                .addOnSuccessListener(querySnapshot -> Executors.newSingleThreadExecutor().execute(() -> {
                    try (
                            OutputStream os = getContentResolver().openOutputStream(uri);
                            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                            BufferedWriter writer = new BufferedWriter(osw)
                    ) {
                        // BOM para Excel
                        writer.write('\uFEFF');

                        String userName = getUserDisplayName();
                        String reportDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());

                        // Título + fecha
                        writer.write("WineTrack - Colección personal de " + userName);
                        writer.newLine();
                        writer.write("Fecha reporte: " + reportDate);
                        writer.newLine();
                        writer.newLine();

                        // Cabeceras
                        writer.write("Nombre,Variedad,Cosecha,Origen,Categoría,Alcohol %,Precio,Fecha registro,Comentario,Imagen URL");
                        writer.newLine();

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String wineName   = csv(doc.getString("wineName"));
                            String variety    = csv(doc.getString("variety"));
                            String vintage    = csv(doc.getString("vintage"));
                            String origin     = csv(doc.getString("origin"));
                            String category   = csv(doc.getString("category"));

                            String percentage = valueToString(doc.get("percentage"));
                            String price      = valueToString(doc.get("price"));

                            String comment    = csv(doc.getString("comment"));
                            String imageUrl   = csv(doc.getString("imageUrl"));

                            String createdAt = "";
                            if (doc.getTimestamp("createdAt") != null) {
                                createdAt = csv(new SimpleDateFormat(
                                        "dd-MM-yyyy HH:mm",
                                        Locale.getDefault()
                                ).format(doc.getTimestamp("createdAt").toDate()));
                            }

                            String line = String.join(",",
                                    wineName, variety, vintage, origin, category,
                                    percentage, price, createdAt, comment, imageUrl
                            );

                            writer.write(line);
                            writer.newLine();
                        }

                        writer.flush();

                        runOnUiThread(() ->
                                Toast.makeText(this, "CSV exportado ✅", Toast.LENGTH_LONG).show()
                        );
                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(this, "Error al exportar CSV: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
                    }
                }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error leyendo Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    // =========================================================
    // EXPORT PDF - ESTILO FICHA (UNA PÁGINA POR VINO)
    // =========================================================
    private void exportCollectionToPdfResumenYFichas(Uri uri) {
        if (user == null) {
            Toast.makeText(this, "Debes iniciar sesión para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Generando PDF (resumen + fichas)...", Toast.LENGTH_SHORT).show();

        db.collection("descriptions")
                .document(user.getUid())
                .collection("wineDescriptions")
                .get()
                .addOnSuccessListener(querySnapshot -> Executors.newSingleThreadExecutor().execute(() -> {

                    PdfDocument pdf = new PdfDocument();

                    try {
                        String userName = (user.getDisplayName() == null || user.getDisplayName().isEmpty())
                                ? "usuario" : user.getDisplayName();

                        String reportDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());

                        // A4 aprox
                        final int pageWidth = 595;
                        final int pageHeight = 842;
                        final int margin = 36;

                        // Estilo base
                        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        titlePaint.setColor(COLOR_WINE);
                        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        subPaint.setColor(COLOR_GRAY);

                        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        linePaint.setColor(Color.LTGRAY);
                        linePaint.setStrokeWidth(2f);

                        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        footerPaint.setColor(COLOR_GRAY);
                        footerPaint.setTextSize(9);

                        Paint footerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        footerLinePaint.setColor(Color.LTGRAY);
                        footerLinePaint.setStrokeWidth(1.5f);

                        // =========================================================
                        // 1) PÁGINA(S) DE RESUMEN (TABLA)
                        // =========================================================
                        Paint tableHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        tableHeaderPaint.setColor(Color.BLACK);
                        tableHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        tableHeaderPaint.setTextSize(10);

                        Paint tableCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        tableCellPaint.setColor(Color.DKGRAY);
                        tableCellPaint.setTextSize(9);

                        Paint tableGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        tableGridPaint.setColor(Color.LTGRAY);
                        tableGridPaint.setStrokeWidth(1f);

                        int pageNumber = 1;

                        // Columnas resumen
                        int colName = 180;
                        int colVariety = 90;
                        int colVintage = 55;
                        int colOrigin = 110;
                        int colCategory = 70;
                        int colPrice = 60;

                        int tableX = margin;
                        int tableYStart; // después del header
                        int rowH = 18;

                        PdfDocument.Page summaryPage = pdf.startPage(
                                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        );
                        Canvas canvas = summaryPage.getCanvas();

                        int y = margin;

                        // Header del documento (Resumen)
                        titlePaint.setTextSize(18);
                        canvas.drawText("WineTrack", margin, y, titlePaint);
                        y += 18;

                        subPaint.setTextSize(12);
                        canvas.drawText("Colección personal de " + userName, margin, y, subPaint);
                        y += 14;

                        canvas.drawText("Fecha reporte: " + reportDate, margin, y, subPaint);
                        y += 16;

                        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                        y += 18;

                        titlePaint.setTextSize(16);
                        canvas.drawText("Resumen de colección", margin, y, titlePaint);
                        y += 18;

                        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                        y += 14;

                        tableYStart = y;

                        // Encabezados de tabla
                        int x = tableX;
                        canvas.drawText("Nombre", x + 2, y, tableHeaderPaint); x += colName;
                        canvas.drawText("Variedad", x + 2, y, tableHeaderPaint); x += colVariety;
                        canvas.drawText("Año", x + 2, y, tableHeaderPaint); x += colVintage;
                        canvas.drawText("Origen", x + 2, y, tableHeaderPaint); x += colOrigin;
                        canvas.drawText("Categoría", x + 2, y, tableHeaderPaint); x += colCategory;
                        canvas.drawText("Precio", x + 2, y, tableHeaderPaint);

                        y += 10;
                        canvas.drawLine(margin, y, pageWidth - margin, y, tableGridPaint);
                        y += 12;

                        // Iteramos documentos para tabla (puede requerir varias páginas)
                        for (QueryDocumentSnapshot doc : querySnapshot) {

                            // Salto de página para resumen
                            if (y > pageHeight - margin - 40) {
                                // Footer resumen
                                drawFooter(canvas, pageWidth, pageHeight, margin, footerPaint, footerLinePaint, pageNumber);

                                pdf.finishPage(summaryPage);

                                pageNumber++;
                                summaryPage = pdf.startPage(
                                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                                );
                                canvas = summaryPage.getCanvas();

                                // Header simple en nuevas páginas de resumen
                                y = margin;
                                titlePaint.setTextSize(14);
                                canvas.drawText("WineTrack - Resumen de colección", margin, y, titlePaint);
                                y += 14;

                                subPaint.setTextSize(10);
                                canvas.drawText("Colección de " + userName + " | Fecha: " + reportDate, margin, y, subPaint);
                                y += 12;

                                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                                y += 16;

                                // Encabezados tabla repetidos
                                x = tableX;
                                canvas.drawText("Nombre", x + 2, y, tableHeaderPaint); x += colName;
                                canvas.drawText("Variedad", x + 2, y, tableHeaderPaint); x += colVariety;
                                canvas.drawText("Año", x + 2, y, tableHeaderPaint); x += colVintage;
                                canvas.drawText("Origen", x + 2, y, tableHeaderPaint); x += colOrigin;
                                canvas.drawText("Categoría", x + 2, y, tableHeaderPaint); x += colCategory;
                                canvas.drawText("Precio", x + 2, y, tableHeaderPaint);

                                y += 10;
                                canvas.drawLine(margin, y, pageWidth - margin, y, tableGridPaint);
                                y += 12;
                            }

                            String wineName = safe(doc.getString("wineName"));
                            String variety = safe(doc.getString("variety"));
                            String vintage = safe(doc.getString("vintage"));
                            String origin = safe(doc.getString("origin"));
                            String category = safe(doc.getString("category"));
                            String price = valueToString(doc.get("price"));
                            if (!price.isEmpty() && !price.startsWith("$")) price = "$" + price;

                            // Fila
                            x = tableX;
                            canvas.drawText(clip(wineName, 36), x + 2, y, tableCellPaint); x += colName;
                            canvas.drawText(clip(variety, 14), x + 2, y, tableCellPaint); x += colVariety;
                            canvas.drawText(clip(vintage, 6), x + 2, y, tableCellPaint); x += colVintage;
                            canvas.drawText(clip(origin, 18), x + 2, y, tableCellPaint); x += colOrigin;
                            canvas.drawText(clip(category, 12), x + 2, y, tableCellPaint); x += colCategory;
                            canvas.drawText(clip(price, 10), x + 2, y, tableCellPaint);

                            y += rowH;
                        }

                        // Footer última página resumen
                        drawFooter(canvas, pageWidth, pageHeight, margin, footerPaint, footerLinePaint, pageNumber);
                        pdf.finishPage(summaryPage);

                        // =========================================================
                        // 2) FICHAS INDIVIDUALES (TU ESTILO ACTUAL)
                        // =========================================================
                        Paint sectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        sectionPaint.setColor(Color.BLACK);
                        sectionPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                        sectionPaint.setTextSize(12);

                        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        bodyPaint.setColor(Color.DKGRAY);
                        bodyPaint.setTextSize(10);

                        // Columnas ficha
                        final int leftX = margin;
                        final int rightX = pageWidth / 2 + 10;
                        final int leftWidth = (pageWidth / 2) - margin - 20;
                        final int rightWidth = pageWidth - rightX - margin;

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            pageNumber++;

                            // Datos
                            String wineName = safe(doc.getString("wineName"));
                            String variety = safe(doc.getString("variety"));
                            String vintage = safe(doc.getString("vintage"));
                            String origin = safe(doc.getString("origin"));
                            String category = safe(doc.getString("category"));

                            String percentage = valueToString(doc.get("percentage"));
                            String price = valueToString(doc.get("price"));
                            if (!price.isEmpty() && !price.startsWith("$")) price = "$" + price;

                            String comment = safe(doc.getString("comment"));
                            String imageUrl = safe(doc.getString("imageUrl"));

                            // Producción editorial
                            String production;
                            if (origin.isEmpty() && category.isEmpty() && variety.isEmpty() && vintage.isEmpty()) {
                                production = "Información de producción no registrada.\n"
                                        + "Puedes complementar esta sección agregando datos como bodega, crianza o barrica.";
                            } else {
                                StringBuilder sb = new StringBuilder();
                                if (!origin.isEmpty()) sb.append("Origen: ").append(origin).append("\n");
                                if (!category.isEmpty()) sb.append("Categoría: ").append(category).append("\n");
                                if (!variety.isEmpty()) sb.append("Variedad: ").append(variety).append("\n");
                                if (!vintage.isEmpty()) sb.append("Cosecha: ").append(vintage).append("\n");
                                production = sb.toString().trim();
                            }

                            PdfDocument.Page page = pdf.startPage(
                                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            );
                            Canvas c = page.getCanvas();

                            int yy = margin;

                            // Header ficha
                            titlePaint.setTextSize(18);
                            c.drawText("WineTrack", leftX, yy, titlePaint);
                            yy += 18;

                            subPaint.setTextSize(12);
                            c.drawText("Colección personal de " + userName, leftX, yy, subPaint);
                            yy += 14;

                            c.drawText("Fecha reporte: " + reportDate, leftX, yy, subPaint);
                            yy += 16;

                            c.drawLine(margin, yy, pageWidth - margin, yy, linePaint);
                            yy += 22;

                            // Nombre vino
                            titlePaint.setTextSize(20);
                            c.drawText(clip(wineName, 42), leftX, yy, titlePaint);
                            yy += 22;

                            subPaint.setTextSize(12);
                            if (!origin.isEmpty()) {
                                c.drawText(origin, leftX, yy, subPaint);
                                yy += 16;
                            } else {
                                yy += 8;
                            }

                            c.drawLine(margin, yy, pageWidth - margin, yy, linePaint);
                            yy += 22;

                            int yColumnsStart = yy;

                            // Imagen derecha
                            int ry = yColumnsStart;
                            Bitmap bottle = downloadBitmap(imageUrl);
                            if (bottle != null) {
                                int maxImgWidth = rightWidth;
                                int maxImgHeight = 320;

                                int bmpW = bottle.getWidth();
                                int bmpH = bottle.getHeight();

                                float scale = Math.min((float) maxImgWidth / bmpW, (float) maxImgHeight / bmpH);
                                int scaledW = Math.max(1, (int) (bmpW * scale));
                                int scaledH = Math.max(1, (int) (bmpH * scale));

                                Bitmap scaled = Bitmap.createScaledBitmap(bottle, scaledW, scaledH, true);
                                Bitmap rounded = getRoundedBitmap(scaled, 18f);

                                int imgX = rightX + (rightWidth - scaledW) / 2;
                                int imgY = ry;

                                c.drawBitmap(rounded, imgX, imgY, null);
                            }

                            // Columna izquierda
                            int leftY = yColumnsStart;

                            c.drawText("Producción", leftX, leftY, sectionPaint);
                            leftY += 14;
                            leftY = drawParagraph(c, production, leftX, leftY, leftWidth, bodyPaint, 10);
                            leftY += 14;

                            c.drawText("Notas de cata", leftX, leftY, sectionPaint);
                            leftY += 14;
                            leftY = drawParagraph(c,
                                    comment.isEmpty() ? "Sin comentarios." : comment,
                                    leftX, leftY, leftWidth, bodyPaint, 14);
                            leftY += 14;

                            c.drawText("Resumen", leftX, leftY, sectionPaint);
                            leftY += 14;

                            String resumen =
                                    "Categoría: " + (category.isEmpty() ? "—" : category) + "\n" +
                                            "Variedad: " + (variety.isEmpty() ? "—" : variety) + "\n" +
                                            "Cosecha: " + (vintage.isEmpty() ? "—" : vintage) + "\n" +
                                            "Alcohol: " + (percentage.isEmpty() ? "—" : percentage + "%") + "\n" +
                                            "Precio: " + (price.isEmpty() ? "—" : price);

                            leftY = drawParagraph(c, resumen, leftX, leftY, leftWidth, bodyPaint, 10);

                            // Footer
                            drawFooter(c, pageWidth, pageHeight, margin, footerPaint, footerLinePaint, pageNumber);

                            pdf.finishPage(page);
                        }

                        // Guardar
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            pdf.writeTo(os);
                        }

                        runOnUiThread(() ->
                                Toast.makeText(this, "PDF exportado ✅", Toast.LENGTH_LONG).show()
                        );

                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(this, "Error exportando PDF: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
                    } finally {
                        pdf.close();
                    }

                }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error leyendo Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    /** Footer reutilizable */
    private void drawFooter(Canvas canvas, int pageWidth, int pageHeight, int margin,
                            Paint footerPaint, Paint footerLinePaint, int pageNumber) {

        int footerY = pageHeight - margin;

        canvas.drawLine(margin, footerY - 14, pageWidth - margin, footerY - 14, footerLinePaint);

        String footerText = "Generado por WineTrack";
        float textW = footerPaint.measureText(footerText);
        float footerX = (pageWidth - textW) / 2f;
        canvas.drawText(footerText, footerX, footerY, footerPaint);

        String pageText = "Página " + pageNumber;
        canvas.drawText(pageText,
                pageWidth - margin - footerPaint.measureText(pageText),
                footerY,
                footerPaint);
    }


    // =========================================================
    // UI
    // =========================================================
    private void initViews() {
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        txtProfileUid = findViewById(R.id.txtProfileUid);
        btnEditName = findViewById(R.id.btnEditName);
        btnEditEmail = findViewById(R.id.btnEditEmail);
        switchBiometricGate = findViewById(R.id.switchBiometricGate);

        btnChangePassword = findViewById(R.id.btnChangePassword);

        switchRememberSession = findViewById(R.id.switchRememberSession);

        switchNotifOptimal = findViewById(R.id.switchNotifOptimal);
        switchNotifExpiry = findViewById(R.id.switchNotifExpiry);
        switchNotifNews = findViewById(R.id.switchNotifNews);

        rgTheme = findViewById(R.id.rgTheme);
        rbThemeLight = findViewById(R.id.rbThemeLight);
        rbThemeDark = findViewById(R.id.rbThemeDark);
        rbThemeSystem = findViewById(R.id.rbThemeSystem);

        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnExport = findViewById(R.id.btnExport);

        txtAboutVersion = findViewById(R.id.txtAboutVersion);
        txtPrivacyPolicy = findViewById(R.id.txtPrivacyPolicy);
        txtTerms = findViewById(R.id.txtTerms);
    }

    private void loadUserInfo() {
        if (user == null) return;

        String name = user.getDisplayName();
        if (name == null || name.isEmpty()) name = "Amante del vino";

        txtProfileName.setText(name);
        txtProfileEmail.setText(user.getEmail());
        txtProfileUid.setText("UID: " + user.getUid());
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
        if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) rbThemeLight.setChecked(true);
        else if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) rbThemeDark.setChecked(true);
        else rbThemeSystem.setChecked(true);
    }

    private void setupListeners() {

        btnChangePassword.setOnClickListener(v -> {
            if (user != null && user.getEmail() != null) {
                FirebaseAuth.getInstance()
                        .sendPasswordResetEmail(user.getEmail())
                        .addOnSuccessListener(unused ->
                                Toast.makeText(this, "Te enviamos un correo para cambiar la contraseña", Toast.LENGTH_LONG).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Error al enviar correo: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });

        switchBiometricGate.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("biometric_gate_enabled", isChecked).apply()
        );

        switchRememberSession.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("remember_session", isChecked).apply();
            if (!isChecked) {
                prefs.edit().putBoolean("biometric_gate_enabled", false).apply();
                switchBiometricGate.setChecked(false);
            }
        });

        switchNotifOptimal.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_optimal", isChecked).apply()
        );
        switchNotifExpiry.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_expiry", isChecked).apply()
        );
        switchNotifNews.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("notif_news", isChecked).apply()
        );

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rbThemeLight) mode = AppCompatDelegate.MODE_NIGHT_NO;
            else if (checkedId == R.id.rbThemeDark) mode = AppCompatDelegate.MODE_NIGHT_YES;
            else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

            prefs.edit().putInt("theme_mode", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
            recreate();
        });

        txtPrivacyPolicy.setOnClickListener(v -> openUrl("https://tusitio.cl/politica-privacidad"));
        txtTerms.setOnClickListener(v -> openUrl("https://tusitio.cl/terminos-uso"));

        btnEditName.setOnClickListener(v ->
                Toast.makeText(this, "Cambiar nombre (por implementar)", Toast.LENGTH_SHORT).show());
        btnEditEmail.setOnClickListener(v ->
                Toast.makeText(this, "Cambiar correo (por implementar)", Toast.LENGTH_SHORT).show());

        btnBackup.setOnClickListener(v ->
                Toast.makeText(this, "Backup en Firestore (por implementar)", Toast.LENGTH_SHORT).show());
        btnRestore.setOnClickListener(v ->
                Toast.makeText(this, "Restaurar backup (por implementar)", Toast.LENGTH_SHORT).show());

        btnExport.setOnClickListener(v -> showExportDialog());
    }

    private void showExportDialog() {
        String[] options = {"Exportar a CSV (Excel)", "Exportar a PDF (fichas)"};

        new AlertDialog.Builder(this)
                .setTitle("Exportar colección")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        String fileName = "WineTrack_Coleccion_" + getTimestamp() + ".csv";
                        createCsvLauncher.launch(fileName);
                    } else {
                        String fileName = "WineTrack_Resumen_y_Fichas_" + getTimestamp() + ".pdf";
                        createPdfLauncher.launch(fileName);
                    }
                })
                .show();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private String getUserDisplayName() {
        if (user == null) return "usuario";
        String n = user.getDisplayName();
        return (n == null || n.isEmpty()) ? "usuario" : n;
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    private String valueToString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String csv(String value) {
        if (value == null) return "";
        boolean mustQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String v = value.replace("\"", "\"\"");
        return mustQuote ? ("\"" + v + "\"") : v;
    }

    private void drawKeyValue(Canvas canvas, int x, int y,
                              Paint labelPaint, Paint valuePaint,
                              String label, String value, int maxWidth) {

        canvas.drawText(label, x, y, labelPaint);

        float labelW = labelPaint.measureText(label + " ");
        String v = (value == null) ? "" : value;

        // recorte simple por ancho aproximado (por chars)
        if (v.length() > 26) v = v.substring(0, 25) + "…";
        canvas.drawText(v, x + (int) labelW, y, valuePaint);
    }

    /**
     * Dibuja párrafo haciendo wrap por palabras dentro de un ancho.
     * Devuelve el nuevo Y final.
     */
    private int drawParagraph(Canvas canvas, String text, int x, int y, int maxWidth, Paint paint, int maxLines) {
        if (text == null) text = "";
        String[] words = text.replace("\r", " ").split("\\s+");

        StringBuilder line = new StringBuilder();
        int lines = 0;

        for (String w : words) {
            String test = line.length() == 0 ? w : (line + " " + w);
            if (paint.measureText(test) <= maxWidth) {
                line = new StringBuilder(test);
            } else {
                canvas.drawText(line.toString(), x, y, paint);
                y += 14;
                lines++;
                if (lines >= maxLines) {
                    canvas.drawText("…", x, y, paint);
                    return y + 14;
                }
                line = new StringBuilder(w);
            }
        }

        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += 14;
        }

        return y;
    }

    private Bitmap downloadBitmap(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            return bitmap;

        } catch (Exception e) {
            return null;
        }
    }


    private Bitmap getRoundedBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null) return null;

        Bitmap output = Bitmap.createBitmap(
                bitmap.getWidth(),
                bitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);

        canvas.drawRoundRect(
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                radius,
                radius,
                paint
        );

        paint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SRC_IN
        ));

        canvas.drawBitmap(bitmap, 0, 0, paint);

        return output;
    }


}
