package ru.funeralagency.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ApiClient apiClient;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        apiClient = new ApiClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsJsonResponse() throws Exception {
        server.createContext("/api/test", exchange -> respond(
                exchange,
                200,
                "{\"id\":7,\"name\":\"Тест\"}".getBytes(StandardCharsets.UTF_8)
        ));

        JsonNode response = apiClient.get("/api/test");

        assertEquals(7, response.path("id").asInt());
        assertEquals("Тест", response.path("name").asText());
    }

    @Test
    void sendsJsonBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/test", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "{\"id\":1}".getBytes(StandardCharsets.UTF_8));
        });

        JsonNode request = objectMapper.readTree("{\"fullName\":\"Иванов Иван\"}");
        apiClient.post("/api/test", request);

        assertEquals("POST", method.get());
        assertEquals("Иванов Иван", objectMapper.readTree(body.get()).path("fullName").asText());
    }

    @Test
    void returnsMessageFromServerError() throws Exception {
        server.createContext("/api/test", exchange -> respond(
                exchange,
                400,
                "{\"message\":\"Некорректные данные\"}".getBytes(StandardCharsets.UTF_8)
        ));

        IOException exception = assertThrows(
                IOException.class,
                () -> apiClient.get("/api/test")
        );

        assertEquals("Некорректные данные", exception.getMessage());
    }

    @Test
    void downloadsFile() throws Exception {
        byte[] file = {1, 2, 3, 4};
        server.createContext("/api/export", exchange -> respond(exchange, 200, file));

        assertArrayEquals(file, apiClient.download("/api/export"));
    }

    private void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
