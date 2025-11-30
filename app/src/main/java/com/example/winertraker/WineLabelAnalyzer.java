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
        void onResult(@Nullable WineLabelInfo info,
                      @Nullable String rawOcrText,
                      @Nullable Exception error);
    }

    private static final String TAG = "WineLabelAnalyzer";

    public static void analyzeImage(Context context, Uri imageUri, CallbackResult callback) {

        // DEBUG: ver si la API key llega
        Log.d(TAG, "OPENAI_API_KEY length = " +
                (BuildConfig.OPENAI_API_KEY != null ? BuildConfig.OPENAI_API_KEY.length() : 0));

        // Si no hay API key configurada => directo a OCR local
        if (BuildConfig.OPENAI_API_KEY == null || BuildConfig.OPENAI_API_KEY.isEmpty()) {
            runLocalOcr(context, imageUri, callback);
            return;
        }

        // Convertir imagen a data URL base64
        String dataUrl;
        try {
            dataUrl = imageUriToDataUrl(context, imageUri);
        } catch (IOException e) {
            Log.e(TAG, "Error convirtiendo imagen a base64, usando OCR local", e);
            Toast.makeText(context, "No se pudo preparar la imagen. Usando OCR local.", Toast.LENGTH_SHORT).show();
            runLocalOcr(context, imageUri, callback);
            return;
        }

        // Avisamos que estamos usando OpenAI
        Toast.makeText(context, "Analizando con OpenAI...", Toast.LENGTH_SHORT).show();

        Map<String, Object> body = OpenAiClient.buildVisionRequestBody(dataUrl);
        OpenAiApiService service = OpenAiClient.getApiService();
        Call<OpenAiChatResponse> call = service.createChatCompletion(body);

        call.enqueue(new Callback<OpenAiChatResponse>() {
            @Override
            public void onResponse(Call<OpenAiChatResponse> call, Response<OpenAiChatResponse> response) {
                Log.d(TAG, "OpenAI HTTP code = " + response.code());

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

                    Log.w(TAG, "Respuesta OpenAI no exitosa, se usa OCR local");
                    runLocalOcr(context, imageUri, callback);
                    return;
                }


                OpenAiChatResponse resp = response.body();
                if (resp.choices == null
                        || resp.choices.isEmpty()
                        || resp.choices.get(0).message == null
                        || resp.choices.get(0).message.content == null) {
                    Log.w(TAG, "OpenAI respuesta sin contenido, se usa OCR local");
                    runLocalOcr(context, imageUri, callback);
                    return;
                }

                String contentText = resp.choices.get(0).message.content.trim();
                Log.d(TAG, "OpenAI raw content: " + contentText);

                try {
                    WineLabelInfo info = JsonUtils.fromJson(contentText, WineLabelInfo.class);
                    callback.onResult(info, null, null);
                } catch (Exception ex) {
                    Log.e(TAG, "Error parseando JSON devuelto por OpenAI, usando OCR local", ex);
                    runLocalOcr(context, imageUri, callback);
                }
            }

            @Override
            public void onFailure(Call<OpenAiChatResponse> call, Throwable t) {
                Log.e(TAG, "Error llamando a OpenAI, se usa OCR local", t);
                runLocalOcr(context, imageUri, callback);
            }
        });
    }

    // ---------- Helpers privados ----------

    private static String imageUriToDataUrl(Context context, Uri uri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) throw new IOException("No se pudo abrir InputStream");

        byte[] bytes = readAllBytes(inputStream);
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return "data:image/jpeg;base64," + base64; // asumimos JPG
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
}
