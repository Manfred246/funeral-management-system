package ru.funeralagency.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ApiClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApiClient() {
        this.baseUrl = getBaseUrl();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        return send(request);
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            checkStatus(response.statusCode(), response.body());

            return response.body();
        } catch (ConnectException e) {
            throw new IOException("Не удалось подключиться к серверу" + baseUrl, e);
        }
    }

    private void checkStatus(int statusCode, String responseBody) throws IOException {
        if (statusCode >= 200 && statusCode < 300) { return; }

        String message = "Ошибка сервера: HTTP " + statusCode;

        try {
            var json = objectMapper.readTree(responseBody);

            String serverMessage = json.path("message").asText("");

            if (serverMessage.isBlank()){
                message = serverMessage;
            }
        } catch (Exception ignored){}

        throw new IOException(message);
    }

    private static String getBaseUrl(){
        String url = System.getenv("FUNERAL_API_URL");

        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }

        return url.trim().replaceAll("/+$", "");
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
