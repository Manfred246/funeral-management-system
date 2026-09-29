package ru.funeralagency.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;

    public ApiClient() {
        String configured = System.getenv("FUNERAL_API_URL");
        baseUrl = (configured == null || configured.isBlank()
                ? "http://localhost:8080" : configured).replaceAll("/+$", "");
    }

    public JsonNode get(String path) throws IOException, InterruptedException {
        return json.readTree(send("GET", path, null).body());
    }

    public JsonNode post(String path, JsonNode body) throws IOException, InterruptedException {
        return json.readTree(send("POST", path, body).body());
    }

    public JsonNode put(String path, JsonNode body) throws IOException, InterruptedException {
        return json.readTree(send("PUT", path, body).body());
    }

    public void delete(String path) throws IOException, InterruptedException {
        send("DELETE", path, null);
    }

    public byte[] download(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IOException("Ошибка сервера: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (ConnectException e) {
            throw new IOException("Не удалось подключиться к серверу " + baseUrl, e);
        }
    }

    private HttpResponse<String> send(String method, String path, JsonNode body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String message = json.readTree(response.body()).path("message").asText();
                throw new IOException(message.isBlank()
                        ? "Ошибка сервера: HTTP " + response.statusCode() : message);
            }
            return response;
        } catch (ConnectException e) {
            throw new IOException("Не удалось подключиться к серверу " + baseUrl, e);
        }
    }
}
