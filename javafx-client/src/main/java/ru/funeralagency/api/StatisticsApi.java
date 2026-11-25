package ru.funeralagency.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import ru.funeralagency.dto.StatisticsDto;

import java.io.IOException;

public class StatisticsApi {
    private final ApiClient apiClient;

    public StatisticsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public StatisticsDto getStatistics() throws IOException, InterruptedException {
        String json = apiClient.get("/api/statistics");
        try {
            return apiClient.getObjectMapper().readValue(json, StatisticsDto.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Не удалось прочитать ответ сервера", exception);
        }
    }
}
