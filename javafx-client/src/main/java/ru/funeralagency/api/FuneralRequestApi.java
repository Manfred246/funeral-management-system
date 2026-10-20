package ru.funeralagency.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import ru.funeralagency.dto.CeremonyType;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestDTO;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.dto.RequestStatus;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FuneralRequestApi {
    private final ApiClient apiClient;

    public FuneralRequestApi(ApiClient apiClient){
        this.apiClient = apiClient;
    }

    public List<FuneralRequestDTO> findAll() throws IOException, InterruptedException {
        return readRequests(apiClient.get("/api/requests"));
    }

    public List<FuneralRequestDTO> searchByDeceasedName(String deceasedName)
            throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(deceasedName, StandardCharsets.UTF_8);
        return readRequests(apiClient.get("/api/requests/search?deceasedName=" + encodedName));
    }

    public List<FuneralRequestDTO> searchByClient(Long clientId)
            throws IOException, InterruptedException {
        return readRequests(apiClient.get("/api/requests/search?clientId=" + clientId));
    }

    public List<FuneralRequestDTO> filter(
            RequestStatus status, CeremonyType ceremonyType, LocalDate dateFrom, LocalDate dateTo
    ) throws IOException, InterruptedException {
        List<String> parameters = new ArrayList<>();
        if (status != null) {
            parameters.add("status=" + status.name());
        }
        if (ceremonyType != null) {
            parameters.add("ceremonyType=" + ceremonyType.name());
        }
        if (dateFrom != null) {
            parameters.add("dateFrom=" + dateFrom);
        }
        if (dateTo != null) {
            parameters.add("dateTo=" + dateTo);
        }

        String path = "/api/requests/filter";
        if (!parameters.isEmpty()) {
            path += "?" + String.join("&", parameters);
        }
        return readRequests(apiClient.get(path));
    }

    public FuneralRequestDTO create(FuneralRequestCreateDto request)
            throws IOException, InterruptedException {
        String json = apiClient.post("/api/requests", request);
        try {
            return apiClient.getObjectMapper().readValue(json, FuneralRequestDTO.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Не удалось прочитать ответ сервера", exception);
        }
    }

    public FuneralRequestDTO update(Long id, FuneralRequestUpdateDto request)
            throws IOException, InterruptedException {
        String json = apiClient.put("/api/requests/" + id, request);
        try {
            return apiClient.getObjectMapper().readValue(json, FuneralRequestDTO.class);
        } catch (JsonProcessingException exception) {
            throw new IOException("Не удалось прочитать ответ сервера", exception);
        }
    }

    public void delete(Long id) throws IOException, InterruptedException {
        apiClient.delete("/api/requests/" + id);
    }

    public byte[] exportRequests() throws IOException, InterruptedException {
        return apiClient.download("/api/requests/export");
    }

    private List<FuneralRequestDTO> readRequests(String json) throws IOException {
        try {
            return apiClient.getObjectMapper().readValue(
                    json,
                    new TypeReference<List<FuneralRequestDTO>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IOException("Не удалось прочитать ответ сервера", exception);
        }
    }
}
