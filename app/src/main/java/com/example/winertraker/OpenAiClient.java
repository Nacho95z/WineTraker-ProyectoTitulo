package com.example.winertraker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente singleton para llamar a la API de OpenAI.
 */
public class OpenAiClient {

    private static OpenAiApiService apiService;

    public static OpenAiApiService getApiService() {
        if (apiService == null) {

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            Request.Builder builder = original.newBuilder()
                                    .header("Authorization", "Bearer " + BuildConfig.OPENAI_API_KEY);

                            Request request = builder.build();
                            return chain.proceed(request);
                        }
                    })
                    .build();

            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.openai.com/") // base
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            apiService = retrofit.create(OpenAiApiService.class);
        }
        return apiService;
    }

    /**
     * Construye el body para una petición de visión con chat completions.
     * Recibe la imagen en formato "data:image/jpeg;base64,...."
     */
    public static Map<String, Object> buildVisionRequestBody(String dataUrlImage) {

        Map<String, Object> body = new HashMap<>();

        // ✅ Modelo que soporta visión
        body.put("model", "gpt-4o-mini");

        // -------- MENSAJE DE SISTEMA --------
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "Eres un asistente experto en etiquetas de vino. " +
                        "Debes devolver EXCLUSIVAMENTE un JSON válido con las claves: " +
                        "wineName, variety, vintage, origin, percentage. " +
                        "Sin texto adicional antes ni después del JSON."
        );

        // -------- MENSAJE DE USUARIO (texto + imagen) --------
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        java.util.List<Map<String, Object>> contentList = new java.util.ArrayList<>();

        // Bloque de texto
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text",
                "Analiza esta imagen de una etiqueta de vino y devuelve un JSON con:\n" +
                        "wineName, variety, vintage, origin, percentage.\n" +
                        "Si un dato no está visible usa \"\"."
        );
        contentList.add(textBlock);

        // Bloque de imagen
        Map<String, Object> imageBlock = new HashMap<>();
        imageBlock.put("type", "image_url");
        Map<String, Object> imageUrlObject = new HashMap<>();
        imageUrlObject.put("url", dataUrlImage);
        imageBlock.put("image_url", imageUrlObject);
        contentList.add(imageBlock);

        userMessage.put("content", contentList);

        // -------- LISTA FINAL DE MENSAJES --------
        java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);

        body.put("messages", messages);

        // Pedimos JSON estricto
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        body.put("temperature", 0.2);

        return body;
    }

}
