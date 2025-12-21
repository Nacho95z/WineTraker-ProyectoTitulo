package com.example.winertraker;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Primero intenta OpenAI Vision.
 * Si falla o no hay API key, usa OCR local (ML Kit) como fallback.
 */
public class WineLabelAnalyzer {

    public interface CallbackResult {
        void onProgress(String stage);
        void onResult(@Nullable WineLabelInfo info,
                      @Nullable String rawOcrText,
                      @Nullable Exception error);
    }


    private static final String TAG = "WineLabelAnalyzer";

    public static void analyzeImage(Context context, Uri imageUri, CallbackResult callback) {

        // DEBUG: ver si la API key llega
        Log.d(TAG, "OPENAI_API_KEY length = " +
                (BuildConfig.OPENAI_API_KEY != null ? BuildConfig.OPENAI_API_KEY.length() : 0));

        // Si no hay API KEY configurada → usa OCR local
        if (BuildConfig.OPENAI_API_KEY == null || BuildConfig.OPENAI_API_KEY.isEmpty()
                || BuildConfig.OPENAI_API_KEY.equals("REPLACE_ME")) {

            Log.e(TAG, "API KEY de OpenAI no configurada. Usando OCR local por defecto.");
            Toast.makeText(context, "OpenAI no está configurado. Usando OCR local.", Toast.LENGTH_SHORT).show();

            runLocalOcr(context, imageUri, callback);
            return;
        }

        // ==========================
        // ⏱️ Medición de tiempos
        // ==========================
        final long tStartTotal = System.currentTimeMillis();

        // Convertir imagen a data URL base64
        String dataUrl;
        final long tStartPrep = System.currentTimeMillis();
        try {
            callback.onProgress("Preparando imagen…");
            dataUrl = imageUriToDataUrl(context, imageUri);
        } catch (IOException e) {
            long prepMs = System.currentTimeMillis() - tStartPrep;
            Log.e(TAG, "Error convirtiendo imagen a base64 (prepMs=" + prepMs + "), usando OCR local", e);
            Toast.makeText(context, "No se pudo preparar la imagen. Usando OCR local.", Toast.LENGTH_SHORT).show();
            runLocalOcr(context, imageUri, callback);
            return;
        }
        final long prepMs = System.currentTimeMillis() - tStartPrep;

        // Construir request OpenAI
        Map<String, Object> body = OpenAiClient.buildVisionRequestBody(dataUrl);
        OpenAiApiService service = OpenAiClient.getApiService();

        callback.onProgress("Enviando imagen a OpenAI…");
        Call<OpenAiChatResponse> call = service.createChatCompletion(body);

        // ⏱️ Start roundtrip OpenAI (desde enqueue hasta respuesta)
        final long tStartOpenAi = System.currentTimeMillis();

        callback.onProgress("Analizando etiqueta…");
        call.enqueue(new Callback<OpenAiChatResponse>() {

            @Override
            public void onResponse(Call<OpenAiChatResponse> call, Response<OpenAiChatResponse> response) {

                long openAiMs = System.currentTimeMillis() - tStartOpenAi;
                long totalSoFarMs = System.currentTimeMillis() - tStartTotal;

                Log.d(TAG, "OpenAI HTTP code = " + response.code()
                        + " | prepMs=" + prepMs
                        + " | openAiRoundtripMs=" + openAiMs
                        + " | totalMsSoFar=" + totalSoFarMs);

                if (!response.isSuccessful() || response.body() == null) {
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "OpenAI error body: " + errorBody);

                            if (response.code() == 429) {
                                Toast.makeText(context,
                                        "Límite de uso de IA alcanzado. Usando OCR local.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (IOException ignored) {}

                    Log.w(TAG, "Respuesta OpenAI no exitosa, se usa OCR local. " +
                            "prepMs=" + prepMs + ", openAiRoundtripMs=" + openAiMs);

                    runLocalOcr(context, imageUri, callback);
                    return;
                }

                OpenAiChatResponse resp = response.body();
                if (resp.choices == null
                        || resp.choices.isEmpty()
                        || resp.choices.get(0).message == null
                        || resp.choices.get(0).message.content == null) {

                    Log.w(TAG, "OpenAI respuesta sin contenido, se usa OCR local. " +
                            "prepMs=" + prepMs + ", openAiRoundtripMs=" + openAiMs);

                    runLocalOcr(context, imageUri, callback);
                    return;
                }

                String contentText = resp.choices.get(0).message.content.trim();
                Log.d(TAG, "OpenAI raw content: " + contentText);

                // ⏱️ Parse/interpretación del JSON
                final long tStartParse = System.currentTimeMillis();
                try {
                    callback.onProgress("Interpretando resultados…");
                    WineLabelInfo info = JsonUtils.fromJson(contentText, WineLabelInfo.class);
                    info.normalizeFields(); // ✅ validación defensiva

                    long parseMs = System.currentTimeMillis() - tStartParse;
                    long totalMs = System.currentTimeMillis() - tStartTotal;

                    Log.d(TAG, "TIMING OK -> prepMs=" + prepMs
                            + " | openAiRoundtripMs=" + openAiMs
                            + " | parseMs=" + parseMs
                            + " | totalMs=" + totalMs);

                    callback.onResult(info, null, null);

                } catch (Exception ex) {
                    long parseMs = System.currentTimeMillis() - tStartParse;

                    Log.e(TAG, "Error parseando JSON devuelto por OpenAI " +
                            "(prepMs=" + prepMs + ", openAiRoundtripMs=" + openAiMs + ", parseMs=" + parseMs + "). " +
                            "Usando OCR local", ex);

                    runLocalOcr(context, imageUri, callback);
                }
            }

            @Override
            public void onFailure(Call<OpenAiChatResponse> call, Throwable t) {
                long openAiMs = System.currentTimeMillis() - tStartOpenAi;
                long totalMs = System.currentTimeMillis() - tStartTotal;

                Log.e(TAG, "Error llamando a OpenAI -> prepMs=" + prepMs
                        + " | openAiRoundtripMs=" + openAiMs
                        + " | totalMs=" + totalMs, t);

                runLocalOcr(context, imageUri, callback);
            }
        });
    }


    // ---------- Helpers privados ----------

    private static String imageUriToDataUrl(Context context, Uri uri) throws IOException {

        long t0 = System.currentTimeMillis();

        // 1) Leer solo dimensiones (sin cargar bitmap completo)
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("No se pudo abrir InputStream");
            android.graphics.BitmapFactory.decodeStream(is, null, opts);
        }

        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw new IOException("No se pudieron leer dimensiones de la imagen");
        }

        // 2) Downsample: queremos un "long side" aprox 1024 (puedes subir a 1280 si lo necesitas)
        final int targetLongSide = 1024;
        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, targetLongSide);
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; // menos memoria (más rápido)

        android.graphics.Bitmap decoded;
        try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
            if (is2 == null) throw new IOException("No se pudo abrir InputStream (2)");
            decoded = android.graphics.BitmapFactory.decodeStream(is2, null, opts);
        }

        if (decoded == null) throw new IOException("No se pudo decodificar imagen");

        long t1 = System.currentTimeMillis();

        // 3) (Opcional pero recomendado) crop central para quitar bordes/fondo
        android.graphics.Bitmap cropped = null;
        android.graphics.Bitmap scaled = null;

        try {
            cropped = centerCrop(decoded, 0.92f); // 92% del centro (ajusta 0.88–0.95)
            if (cropped != decoded) decoded.recycle();

            // 4) Asegurar que el long side quede en 1024 exacto (si quieres)
            scaled = resizeKeepingAspectRatio(cropped, 1024);
            if (scaled != cropped) cropped.recycle();

            // 5) Comprimir más agresivo (gran impacto en tiempo)
            //    60–70 es un sweet spot para etiquetas (yo uso 65)
            int jpegQuality = 65;

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality, baos);
            byte[] optimizedBytes = baos.toByteArray();

            long t2 = System.currentTimeMillis();

            String base64 = Base64.encodeToString(optimizedBytes, Base64.NO_WRAP);

            Log.d(TAG, "OpenAI prep ms: decode=" + (t1 - t0) + " | compress=" + (t2 - t1)
                    + " | bytes=" + optimizedBytes.length);

            return "data:image/jpeg;base64," + base64;

        } finally {
            if (scaled != null) scaled.recycle();
        }
    }
    private static int calculateInSampleSize(int width, int height, int targetLongSide) {
        int longSide = Math.max(width, height);
        int inSampleSize = 1;

        while (longSide / inSampleSize > targetLongSide) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    /**
     * Recorte central por porcentaje (0.90f = deja 90% del centro).
     */
    private static android.graphics.Bitmap centerCrop(android.graphics.Bitmap src, float keepPercent) {
        if (src == null) return null;
        keepPercent = Math.max(0.5f, Math.min(1f, keepPercent));

        int w = src.getWidth();
        int h = src.getHeight();

        int newW = Math.round(w * keepPercent);
        int newH = Math.round(h * keepPercent);

        int x = (w - newW) / 2;
        int y = (h - newH) / 2;

        // Si no hay cambio real, devuelve el mismo
        if (newW == w && newH == h) return src;

        return android.graphics.Bitmap.createBitmap(src, x, y, newW, newH);
    }




    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    // Fallback OCR local
    private static void runLocalOcr(Context context, Uri imageUri, CallbackResult callback) {
        callback.onProgress("Usando OCR local…");
        Toast.makeText(context, "OpenAI no respondió. Usando OCR local.", Toast.LENGTH_SHORT).show();
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            com.google.mlkit.vision.text.TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String recognized = text.getText();
                        callback.onResult(null, recognized, null);
                    })
                    .addOnFailureListener(e -> callback.onResult(null, null, e));
        } catch (IOException e) {
            callback.onResult(null, null, e);
        }
    }

    private static android.graphics.Bitmap resizeKeepingAspectRatio(
            android.graphics.Bitmap bitmap,
            int maxSize
    ) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return bitmap; // no necesita resize
        }

        float ratio = (float) width / height;

        int newWidth;
        int newHeight;

        if (ratio > 1) {
            newWidth = maxSize;
            newHeight = Math.round(maxSize / ratio);
        } else {
            newHeight = maxSize;
            newWidth = Math.round(maxSize * ratio);
        }

        return android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                newWidth,
                newHeight,
                true
        );
    }


}
