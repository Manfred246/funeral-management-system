package ru.funeralagency.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import ru.funeralagency.dto.ClientDTO;

import java.io.IOException;
import java.util.List;

public class ClientApi {
    private final ApiClient apiClient;

    public ClientApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<ClientDTO> findAll() throws IOException, InterruptedException {
        String json = apiClient.get("/api/clients");

        try {
            return apiClient.getObjectMapper().readValue(
                    json,
                    new TypeReference<List<ClientDTO>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IOException("Не удалось прочитать ответ сервера", exception);
        }
    }
}
