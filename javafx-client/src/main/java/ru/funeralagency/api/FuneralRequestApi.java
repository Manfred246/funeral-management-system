package ru.funeralagency.api;

import com.fasterxml.jackson.core.type.TypeReference;
import ru.funeralagency.dto.FuneralRequestDTO;

import java.io.IOException;
import java.util.List;

public class FuneralRequestApi {
    private final ApiClient apiClient;

    public FuneralRequestApi(ApiClient apiClient){
        this.apiClient = apiClient;
    }

    public List<FuneralRequestDTO> findAll() throws IOException, InterruptedException {
        String json = apiClient.get("/api/requests");

        return apiClient.getObjectMapper().readValue(
                json,
                new TypeReference<List<FuneralRequestDTO>>(){}
        );
    }
}
