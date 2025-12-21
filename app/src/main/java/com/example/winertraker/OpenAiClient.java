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
import java.util.concurrent.TimeUnit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente singleton para llamar a la API de OpenAI.
 */
public class OpenAiClient {

    private static OpenAiApiService apiService;

    public static OpenAiApiService getApiService() {
        if (apiService == null) {

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
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
        // ✅ Importante: el system define el "contrato" de salida.
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "Eres un asistente experto en etiquetas de vino chileno. " +
                        "Debes devolver EXCLUSIVAMENTE un JSON válido con EXACTAMENTE estas claves:\n" +
                        "wineName, variety, vintage, origin, percentage, category, rawText, comment.\n\n" +

                        "Reglas estrictas:\n" +
                        "- El JSON debe ser válido y no contener texto fuera de él.\n" +
                        "- Todas las claves deben existir siempre.\n" +
                        "- Si un dato no está presente en la etiqueta, devuelve \"\".\n"
        );



        // -------- MENSAJE DE USUARIO (texto + imagen) --------
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        java.util.List<Map<String, Object>> contentList = new java.util.ArrayList<>();

        // Bloque de texto
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put(
                "text",
                "Analiza esta imagen de una etiqueta de vino chileno y devuelve EXCLUSIVAMENTE un JSON.\n\n" +

                        "Definiciones OBLIGATORIAS de campos:\n" +
                        "- wineName: SOLO el nombre comercial principal del vino o marca. " +
                        "NO debe incluir términos como Reserva, Gran Reserva, Selección, Estate, Línea, Serie ni Denominación.\n" +
                        "- category: La línea, clasificación o gama del vino (ej: Reserva, Gran Reserva, Gran Reserva de los Andes, Estate Bottled, Limited Edition).\n" +
                        "- variety: Cepa o mezcla de cepas.\n" +
                        "- vintage: Año de cosecha (YYYY).\n" +
                        "- origin: Valle, región o denominación de origen (ej: Valle del Maipo).\n" +
                        "- percentage: Grado alcohólico si aparece.\n" +
                        "- rawText: Texto literal reconocido en la etiqueta.\n\n" +

                        "Reglas estrictas:\n" +
                        "- NO mezclar wineName con category.\n" +
                        "- Si el nombre contiene palabras como Reserva o Gran Reserva, esas palabras DEBEN ir en category, NO en wineName.\n" +
                        "- wineName debe ser lo más corto y limpio posible.\n\n" +

                        "Instrucciones OBLIGATORIAS para 'comment':\n" +
                        "Genera un comentario breve, redactado con el tono de un enólogo profesional, técnico y objetivo.\n" +
                        "Describe únicamente características propias del vino:\n" +
                        "- estilo general\n" +
                        "- estructura\n" +
                        "- expresión típica de la cepa\n" +
                        "- características esperadas del valle o zona de origen\n" +
                        "- perfil general de la cosecha\n\n" +

                        "Restricciones estrictas para 'comment':\n" +
                        "- NO recomendar consumo, momentos de consumo ni cantidades.\n" +
                        "- NO sugerir maridajes.\n" +
                        "- NO mencionar precios, promociones ni ventas.\n" +
                        "- NO indicar beneficios, efectos ni incentivos a beber.\n" +
                        "- NO usar lenguaje imperativo ni persuasivo.\n" +
                        "- Usar solo lenguaje descriptivo, técnico y neutral.\n\n" +


                "Devuelve SIEMPRE un JSON válido con EXACTAMENTE estas claves:\n" +
                        "{ \"wineName\": \"\", \"category\": \"\", \"variety\": \"\", \"vintage\": \"\", \"origin\": \"\", \"percentage\": \"\", \"rawText\": \"\", \"comment\": \"\" }.\n\n" +

                        "Si un dato no aparece claramente, devuélvelo como cadena vacía."
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
