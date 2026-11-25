package ru.funeralagency.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.dto.CsvApplyDto;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.dto.CsvPreviewDto;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;
import ru.funeralagency.service.FuneralRequestCsvService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FuneralRequestCsvController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class})
class FuneralRequestCsvControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private UserRepository users;
    @MockitoBean private FuneralRequestCsvService csv;

    private String token(Role role) {
        User user = new User();
        user.setLogin("csv-user");
        user.setName("CSV пользователь");
        user.setRoles(Set.of(role));
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.createAccessToken(user);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "requests.csv", "text/csv", "csv-content".getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "MANAGER", "ADMIN"})
    void exportAllowedForAllRoles(Role role) throws Exception {
        when(csv.export(any(), any())).thenReturn("requestCode\r\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/requests/export/csv").header("Authorization", token(role)))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=funeral-requests.csv"));
    }

    @Test
    void exportBindsEveryMainListFilterAndSortWithoutPageLimit() throws Exception {
        when(csv.export(any(), any())).thenReturn(new byte[0]);
        mvc.perform(get("/api/requests/export/csv").header("Authorization", token(Role.VIEWER))
                        .param("q", "Иванов").param("client", "2").param("ceremonyTypeId", "1")
                        .param("status", "CONFIRMED").param("urgent", "true").param("priority", "4")
                        .param("tag", "3").param("archived", "true").param("minPrice", "10")
                        .param("maxPrice", "20").param("sort", "price,asc"))
                .andExpect(status().isOk());
        ArgumentCaptor<FuneralRequestFilterDto> filter = ArgumentCaptor.forClass(FuneralRequestFilterDto.class);
        verify(csv).export(filter.capture(), eq("price,asc"));
        assertEquals("Иванов", filter.getValue().getQ());
        assertEquals(2L, filter.getValue().getClient());
        assertEquals(1L, filter.getValue().getCeremonyTypeId());
        assertEquals(RequestStatus.CONFIRMED, filter.getValue().getStatus());
        assertTrue(filter.getValue().getUrgent());
        assertEquals(4, filter.getValue().getPriority());
        assertEquals(3L, filter.getValue().getTag());
        assertTrue(filter.getValue().isArchived());
        assertEquals("10", filter.getValue().getMinPrice().toString());
        assertEquals("20", filter.getValue().getMaxPrice().toString());
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,preview,403", "VIEWER,apply,403", "MANAGER,preview,200", "MANAGER,apply,200",
            "ADMIN,preview,200", "ADMIN,apply,200"})
    void importRoleMatrix(Role role, String action, int expected) throws Exception {
        when(csv.preview(any(), any())).thenReturn(new CsvPreviewDto(1, 0, List.of()));
        when(csv.apply(any(), any())).thenReturn(new CsvApplyDto(List.of(), List.of()));
        mvc.perform(multipart("/api/requests/import/" + action).file(file()).param("mode", "CREATE_ONLY")
                        .header("Authorization", token(role)))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(csv);
        }
    }

    @Test
    void previewPassesFileAndModeAndReturnsCounts() throws Exception {
        when(csv.preview(any(), eq(CsvImportMode.UPSERT_BY_REQUEST_CODE))).thenReturn(new CsvPreviewDto(2, 0, List.of()));
        mvc.perform(multipart("/api/requests/import/preview").file(file()).param("mode", "UPSERT_BY_REQUEST_CODE")
                        .header("Authorization", token(Role.MANAGER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validCount").value(2))
                .andExpect(jsonPath("$.errorCount").value(0));
        verify(csv).preview(eq(file().getBytes()), eq(CsvImportMode.UPSERT_BY_REQUEST_CODE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"priority=abc", "minPrice=abc", "client=abc", "ceremonyTypeId=abc", "status=BAD", "archived=BAD"})
    void malformedExportParametersAre400(String query) throws Exception {
        mvc.perform(get("/api/requests/export/csv?" + query).header("Authorization", token(Role.VIEWER)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(csv);
    }

    @Test
    void missingFileOrModeAndUnknownModeAre400() throws Exception {
        String authorization = token(Role.MANAGER);
        mvc.perform(multipart("/api/requests/import/preview").param("mode", "CREATE_ONLY")
                        .header("Authorization", authorization)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/requests/import/apply").file(file()).header("Authorization", authorization))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/requests/import/apply").file(file()).param("mode", "BAD")
                        .header("Authorization", authorization)).andExpect(status().isBadRequest());
        verifyNoInteractions(csv);
    }

    @Test
    void missingJwtIs401() throws Exception {
        mvc.perform(get("/api/requests/export/csv")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/requests/import/preview").file(file()).param("mode", "CREATE_ONLY"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/requests/import/apply").file(file()).param("mode", "CREATE_ONLY"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(csv);
    }
}
