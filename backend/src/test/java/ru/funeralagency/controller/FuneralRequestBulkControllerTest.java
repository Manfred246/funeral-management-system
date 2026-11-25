package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.dto.BulkOperation;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.Tag;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.TagRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;
import ru.funeralagency.service.FuneralRequestBulkService;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.service.TagService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FuneralRequestBulkController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, FuneralRequestBulkService.class,
        FuneralRequestService.class, FuneralRequestMapper.class, TagService.class})
class FuneralRequestBulkControllerTest {
    private static final String PATH = "/api/requests/bulk";
    private static final UUID FIRST = UUID.fromString("8d44b5ce-45e2-487d-81d8-b714004db0d7");
    private static final UUID SECOND = UUID.fromString("ec742d94-20f4-48c7-a944-e2e21c8b117d");
    private static final UUID UNKNOWN = UUID.fromString("c09c51ad-0f4d-422f-ad99-a6476916fd17");
    private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 1, 1, 12, 0);
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private FuneralRequestRepository requests;
    @MockitoBean private ClientRepository clients;
    @MockitoBean private CeremonyTypeRepository types;
    @MockitoBean private TagRepository tags;
    @MockitoBean private UserRepository users;
    @MockitoBean private ru.funeralagency.service.AuditService audit;
    private FuneralRequest first;
    private FuneralRequest second;

    @BeforeEach
    void setUp() {
        first = request(1L, FIRST);
        second = request(2L, SECOND);
        when(requests.findByUuid(FIRST)).thenReturn(Optional.of(first));
        when(requests.findByUuid(SECOND)).thenReturn(Optional.of(second));
        when(requests.saveAndFlush(any(FuneralRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tags.findById(3L)).thenAnswer(invocation -> Optional.of(new Tag(3L, "VIP")));
    }

    private FuneralRequest request(Long id, UUID uuid) {
        FuneralRequest request = new FuneralRequest();
        request.setId(id);
        request.setUuid(uuid);
        request.setRequestCode("FR-2026-00000" + id);
        request.setStatus(RequestStatus.NEW);
        request.setCreatedAt(ORIGINAL_TIME);
        request.setUpdatedAt(ORIGINAL_TIME);
        request.setVersion(3L);
        return request;
    }

    private String token(Role role) {
        User user = new User();
        user.setId(1L);
        user.setLogin("bulk-user");
        user.setName("Менеджер");
        user.setRoles(Set.of(role));
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.createAccessToken(user);
    }

    private String body(String operation, String extra, UUID... uuids) {
        StringBuilder json = new StringBuilder("{\"operation\":\"").append(operation).append("\",\"uuids\":[");
        for (int index = 0; index < uuids.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append(uuids[index]).append('"');
        }
        return json.append(']').append(extra).append('}').toString();
    }

    @ParameterizedTest
    @EnumSource(BulkOperation.class)
    void allFiveOperationsFullySucceedUsingExistingService(BulkOperation operation) throws Exception {
        if (operation == BulkOperation.RESTORE) {
            first.setArchived(true);
            second.setArchived(true);
        }
        if (operation == BulkOperation.REMOVE_TAG) {
            first.getTags().add(new Tag(3L, "VIP"));
            second.getTags().add(new Tag(3L, "VIP"));
        }
        String extra = switch (operation) {
            case CHANGE_STATUS -> ",\"status\":\"CONFIRMED\"";
            case ADD_TAG, REMOVE_TAG -> ",\"tagId\":3";
            default -> "";
        };
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(operation.name(), extra, FIRST, SECOND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(2))
                .andExpect(jsonPath("$.successful[0]").value(FIRST.toString()))
                .andExpect(jsonPath("$.successful[1]").value(SECOND.toString()))
                .andExpect(jsonPath("$.errors").isEmpty());
        verify(requests, times(2)).saveAndFlush(any());
        switch (operation) {
            case ARCHIVE -> { assertTrue(first.isArchived()); assertTrue(second.isArchived()); }
            case RESTORE -> { assertFalse(first.isArchived()); assertFalse(second.isArchived()); }
            case CHANGE_STATUS -> {
                assertEquals(RequestStatus.CONFIRMED, first.getStatus());
                assertEquals(RequestStatus.CONFIRMED, second.getStatus());
            }
            case ADD_TAG -> { assertEquals(1, first.getTags().size()); assertEquals(1, second.getTags().size()); }
            case REMOVE_TAG -> { assertTrue(first.getTags().isEmpty()); assertTrue(second.getTags().isEmpty()); }
        }
        assertEquals(FIRST, first.getUuid());
        assertEquals("FR-2026-000001", first.getRequestCode());
        assertEquals(ORIGINAL_TIME, first.getCreatedAt());
        assertTrue(first.getUpdatedAt().isAfter(ORIGINAL_TIME));
        // Здесь repository — mock: сервис не присваивает version; Hibernate проверяется в PostgreSQL IT.
        assertEquals(3L, first.getVersion());
        verifyNoInteractions(clients, types);
        verify(tags, never()).delete(any());
    }

    @Test
    void unknownUuidBetweenValidRecordsDoesNotStopArchive() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ARCHIVE", "", FIRST, UNKNOWN, SECOND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(2))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].uuid").value(UNKNOWN.toString()))
                .andExpect(jsonPath("$.errors[0].message", containsString("не найдена")));
        assertTrue(first.isArchived());
        assertTrue(second.isArchived());
    }

    @Test
    void invalidStatusTransitionReturnsPartialSuccessAndContinues() throws Exception {
        second.setStatus(RequestStatus.CONFIRMED);
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("CHANGE_STATUS", ",\"status\":\"COMPLETED\"", FIRST, SECOND, UNKNOWN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(1))
                .andExpect(jsonPath("$.successful[0]").value(SECOND.toString()))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].uuid").value(FIRST.toString()))
                .andExpect(jsonPath("$.errors[0].message").value("Недопустимый переход статуса: NEW -> COMPLETED"));
        assertEquals(RequestStatus.NEW, first.getStatus());
        assertEquals(ORIGINAL_TIME, first.getUpdatedAt());
        assertEquals(RequestStatus.COMPLETED, second.getStatus());
        verify(requests, never()).saveAndFlush(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD_TAG", "REMOVE_TAG"})
    void archivedTagErrorDoesNotPreventOtherRequest(String operation) throws Exception {
        first.setArchived(true);
        second.getTags().add(new Tag(3L, "VIP"));
        if ("ADD_TAG".equals(operation)) {
            second.getTags().clear();
        }
        mvc.perform(post(PATH).header("Authorization", token(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(operation, ",\"tagId\":3", FIRST, SECOND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful[0]").value(SECOND.toString()))
                .andExpect(jsonPath("$.errors[0].uuid").value(FIRST.toString()))
                .andExpect(jsonPath("$.errors[0].message").value("Нельзя изменять теги архивной заявки"));
        verify(requests, never()).saveAndFlush(first);
        verify(requests).saveAndFlush(second);
    }

    @Test
    void unknownTagProducesPerRequestErrors() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADD_TAG", ",\"tagId\":999", FIRST, SECOND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].message").value("Тег с ID 999 не найден"));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void repeatedUuidIsProcessedOnlyOnce() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ARCHIVE", "", FIRST, FIRST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(1));
        verify(requests, times(1)).findByUuid(FIRST);
        verify(requests, times(1)).saveAndFlush(first);
    }

    @Test
    void unchangedStatusIsSuccessfulWithoutSaveOrVersionAssignment() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("CHANGE_STATUS", ",\"status\":\"NEW\"", FIRST)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.errors").isEmpty());
        assertEquals(3L, first.getVersion());
        assertEquals(ORIGINAL_TIME, first.getUpdatedAt());
        verify(requests, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"optimistic", "integrity", "unexpected"})
    void persistenceErrorsAreSafeAndDoNotStopFollowingItem(String failure) throws Exception {
        RuntimeException exception = switch (failure) {
            case "optimistic" -> new OptimisticLockingFailureException("internal SQL details");
            case "integrity" -> new DataIntegrityViolationException("internal SQL details");
            default -> new IllegalStateException("internal SQL details");
        };
        when(requests.saveAndFlush(first)).thenThrow(exception);
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ARCHIVE", "", FIRST, SECOND)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful[0]").value(SECOND.toString()))
                .andExpect(jsonPath("$.errors[0].uuid").value(FIRST.toString()))
                .andExpect(content().string(not(containsString("internal SQL details"))));
        verify(requests).saveAndFlush(second);
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,403", "MANAGER,200", "ADMIN,200"})
    void roleMatrix(Role role, int expected) throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(role)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ARCHIVE", "", FIRST)))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(requests, tags);
        }
    }

    @Test
    void missingJwtIs401() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("ARCHIVE", "", FIRST)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(requests, tags);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"operation\":\"ARCHIVE\"}", "{\"operation\":\"ARCHIVE\",\"uuids\":[]}",
            "{\"operation\":\"ARCHIVE\",\"uuids\":[null]}", "{\"operation\":\"ARCHIVE\",\"uuids\":[\"bad\"]}"})
    void invalidStructureIs400BeforeAnyRequestChanges(String body) throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requests, tags);
    }

    @ParameterizedTest
    @CsvSource(value = {"CHANGE_STATUS|", "CHANGE_STATUS|,\"status\":\"BAD\"", "ADD_TAG|", "REMOVE_TAG|,\"tagId\":0",
            "ADD_TAG|,\"tagId\":-1", "ARCHIVE|,\"status\":\"NEW\"", "RESTORE|,\"tagId\":3",
            "CHANGE_STATUS|,\"status\":\"NEW\",\"tagId\":3", "ADD_TAG|,\"tagId\":3,\"status\":\"NEW\"",
            "PERMANENT_DELETE|"}, delimiter = '|', nullValues = "")
    void invalidOperationArgumentsAre400(String operation, String extra) throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(operation, extra == null ? "" : extra, FIRST)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requests, tags);
    }
}
