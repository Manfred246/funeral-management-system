package ru.funeralagency.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Выполняет HTTP-запросы к backend и преобразует JSON-ответы. */
public class ApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApiClient() {
        this(configuredBaseUrl());
    }

    ApiClient(String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = removeTrailingSlashes(baseUrl);
    }

    public JsonNode get(String path) throws IOException, InterruptedException {
        return objectMapper.readTree(send("GET", path, null).body());
    }

    public JsonNode post(String path, JsonNode body) throws IOException, InterruptedException {
        return objectMapper.readTree(send("POST", path, body).body());
    }

    public JsonNode put(String path, JsonNode body) throws IOException, InterruptedException {
        return objectMapper.readTree(send("PUT", path, body).body());
    }

    public void delete(String path) throws IOException, InterruptedException {
        send("DELETE", path, null);
    }

    public byte[] download(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(createUri(path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            checkStatus(
                    response.statusCode(),
                    new String(response.body(), StandardCharsets.UTF_8)
            );
            return response.body();
        } catch (ConnectException exception) {
            throw connectionException(exception);
        }
    }

    private HttpResponse<String> send(String method, String path, JsonNode body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(createUri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");

        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json; charset=UTF-8")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(body)
                            )
                    );
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkStatus(response.statusCode(), response.body());
            return response;
        } catch (ConnectException exception) {
            throw connectionException(exception);
        }
    }

    private void checkStatus(int statusCode, String responseBody) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        String message = readErrorMessage(responseBody);
        if (message.isBlank()) {
            message = "Ошибка сервера: HTTP " + statusCode;
        }
        throw new IOException(message);
    }

    private String readErrorMessage(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody);
            if (error != null) {
                return error.path("message").asText("");
            }
        } catch (IOException ignored) {
            // Сервер может вернуть не JSON, поэтому ниже используем сообщение с HTTP-статусом.
        }
        return "";
    }

    private URI createUri(String path) {
        return URI.create(baseUrl + path);
    }

    private IOException connectionException(ConnectException cause) {
        return new IOException("Не удалось подключиться к серверу " + baseUrl, cause);
    }

    private static String configuredBaseUrl() {
        String configured = System.getenv("FUNERAL_API_URL");
        if (configured == null || configured.isBlank()) {
            return "http://localhost:8080";
        }
        return configured.trim();
    }

    private static String removeTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }
}
