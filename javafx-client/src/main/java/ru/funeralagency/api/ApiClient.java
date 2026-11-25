package ru.funeralagency.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.funeralagency.dto.ErrorResponseDto;

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
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

    public String post(String path, Object body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    public String put(String path, Object body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    public void delete(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .DELETE()
                .build();

        send(request);
    }

    public byte[] download(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                checkStatus(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            }
            return response.body();
        } catch (ConnectException exception) {
            throw new IOException("Не удалось подключиться к серверу " + baseUrl, exception);
        }
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
        } catch (ConnectException exception) {
            throw new IOException("Не удалось подключиться к серверу " + baseUrl, exception);
        }
    }

    private void checkStatus(int statusCode, String responseBody) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        String message = "Ошибка сервера: HTTP " + statusCode;

        try {
            ErrorResponseDto error = objectMapper.readValue(responseBody, ErrorResponseDto.class);
            if (error != null && error.message() != null && !error.message().isBlank()) {
                message = error.message();
            }
        } catch (IOException ignored) {
            // Если сервер вернул не JSON, сохраняем общее сообщение с HTTP-статусом.
        }

        throw new IOException(message);
    }

    private static String getBaseUrl() {
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
