package com.example.winertraker;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

// Modelo de la respuesta de /v1/chat/completions
class OpenAiChatResponse {
    public List<Choice> choices;

    public static class Choice {
        public Message message;
    }

    public static class Message {
        // Como vamos a pedir JSON puro, aquí vendrá el JSON en texto
        public String content;
    }
}

public interface OpenAiApiService {

    @Headers({
            "Content-Type: application/json"
    })
    @POST("v1/chat/completions")
    Call<OpenAiChatResponse> createChatCompletion(@Body Map<String, Object> body);
}
