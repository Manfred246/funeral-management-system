package ru.funeralagency.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsDeletingClientWithRequestsAndKeepsClient() throws Exception {
        long clientId = createClient();
        createRequest(clientId);

        mockMvc.perform(delete("/api/clients/{id}", clientId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Нельзя удалить клиента с ID " + clientId + ": у него есть связанные заявки"
                ))
                .andExpect(jsonPath("$.path").value("/api/clients/" + clientId));

        mockMvc.perform(get("/api/clients/{id}", clientId))
                .andExpect(status().isOk());
    }

    @Test
    void returnsValidationMessage() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Иван",
                                  "phone": "123",
                                  "email": "bad"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "ФИО должно содержать от двух до трёх слов и только буквы, дефис или апостроф"
                ));
    }

    @Test
    void returnsNotFoundMessage() throws Exception {
        mockMvc.perform(get("/api/clients/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Клиент с ID 999999 не найден"));
    }

    @Test
    void returnsMessageForUnreadableBody() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное тело запроса"));
    }

    @Test
    void returnsMessageForInvalidEnumParameter() throws Exception {
        mockMvc.perform(get("/api/funeral-requests/filter")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Некорректное значение параметра: status"
                ));
    }

    @Test
    void deletesClientWithoutRequests() throws Exception {
        long clientId = createClient();

        mockMvc.perform(delete("/api/clients/{id}", clientId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clients/{id}", clientId))
                .andExpect(status().isNotFound());
    }

    private long createClient() throws Exception {
        String response = mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Иван Иванов",
                                  "phone": "+7 999 123 45 67",
                                  "email": "ivan@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.get("id").asLong();
    }

    private void createRequest(long clientId) throws Exception {
        String request = """
                {
                  "clientId": %d,
                  "deceasedFullName": "Пётр Петров",
                  "ceremonyDate": "%s",
                  "ceremonyType": "BURIAL",
                  "price": 10000,
                  "comment": "Тестовая заявка"
                }
                """.formatted(clientId, LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/funeral-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
    }
}
