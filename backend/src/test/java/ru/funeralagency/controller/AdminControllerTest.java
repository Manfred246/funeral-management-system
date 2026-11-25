package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.dto.AdminPasswordResetDto;
import ru.funeralagency.dto.AdminUserCreateDto;
import ru.funeralagency.filter.CorrelationIdFilter;
import ru.funeralagency.model.AuditEvent;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.AuditEventRepository;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;
import ru.funeralagency.service.AdminUserService;
import ru.funeralagency.service.AuditService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminUserController.class, AdminAuditController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class,
        CorrelationIdFilter.class, AdminUserService.class, AuditService.class})
@SuppressWarnings("unchecked")
class AdminControllerTest {
    private static final String PASSWORD = "temporary-password-12";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;
    @MockitoBean private AuditEventRepository events;
    private User admin;
    private User target;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setLogin("admin");
        admin.setName("Администратор");
        target = new User();
        target.setId(2L);
        target.setLogin("viewer");
        target.setName("Пользователь");
        target.setRoles(Set.of(Role.VIEWER));
        target.setPasswordHash("private-old-hash");
        target.setFailedLoginAttempts(5);
        when(users.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(users.findFirstByOrderByIdAsc()).thenReturn(Optional.of(admin));
        when(users.findAll(any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(target), invocation.getArgument(0), 5));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
    }

    private String token(Role role) {
        admin.setRoles(Set.of(role));
        when(users.findByUuid(admin.getUuid())).thenReturn(Optional.of(admin));
        return "Bearer " + jwt.createAccessToken(admin);
    }

    private String body() {
        return "{\"login\":\" new-user \",\"name\":\" Новое имя \",\"roles\":[\"MANAGER\"],\"temporaryPassword\":\"" + PASSWORD + "\"}";
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,GET,users,403", "MANAGER,GET,users,403", "ADMIN,GET,users,200",
            "VIEWER,POST,users,403", "MANAGER,POST,users,403", "ADMIN,POST,users,201",
            "VIEWER,PUT,edit,403", "MANAGER,PUT,edit,403", "ADMIN,PUT,edit,200",
            "VIEWER,PUT,roles,403", "MANAGER,PUT,roles,403", "ADMIN,PUT,roles,200",
            "VIEWER,POST,enable,403", "MANAGER,POST,enable,403", "ADMIN,POST,enable,200",
            "VIEWER,POST,disable,403", "MANAGER,POST,disable,403", "ADMIN,POST,disable,200",
            "VIEWER,POST,reset-password,403", "MANAGER,POST,reset-password,403", "ADMIN,POST,reset-password,200",
            "VIEWER,POST,unlock,403", "MANAGER,POST,unlock,403", "ADMIN,POST,unlock,200",
            "VIEWER,GET,audit,403", "MANAGER,GET,audit,403", "ADMIN,GET,audit,200"})
    void adminOnlyMatrixForEveryEndpoint(Role role, String method, String action, int expected) throws Exception {
        String path = switch (action) {
            case "users", "audit" -> "/api/admin/" + action;
            case "edit" -> "/api/admin/users/" + target.getUuid();
            default -> "/api/admin/users/" + target.getUuid() + "/" + action;
        };
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().is(expected)).andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("private-old-hash"))));
        if (expected == 403) {
            verify(users, never()).saveAndFlush(any());
            verifyNoInteractions(events, sessions);
        }
    }

    @Test
    void listIsPagedAndContainsOnlySafeFields() throws Exception {
        mvc.perform(get("/api/admin/users").param("page", "1").param("size", "2").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].failedLoginAttempts").value(5))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void createStoresBcryptTemporaryPasswordAndAuditsWithoutSecrets() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization", token(Role.ADMIN)).header("X-Correlation-Id", "create-test")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value("new-user"))
                .andExpect(jsonPath("$.name").value("Новое имя")).andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertTrue(encoder.matches(PASSWORD, saved.getValue().getPasswordHash()));
        var event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(event.capture());
        assertEquals("USER_CREATED", event.getValue().getEventType());
        assertEquals(1L, event.getValue().getUserId());
        assertEquals("create-test", event.getValue().getCorrelationId());
        assertFalse(event.getValue().getDescription().contains(PASSWORD));
        assertFalse(event.getValue().getDescription().contains(saved.getValue().getPasswordHash()));
    }

    @Test
    void editRolesDisableEnableUnlockAndResetBehaveAsRequested() throws Exception {
        String authorization = token(Role.ADMIN);
        String path = "/api/admin/users/" + target.getUuid();
        mvc.perform(put(path).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.login").value("new-user"));
        mvc.perform(put(path + "/roles").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"MANAGER\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("MANAGER"));
        mvc.perform(post(path + "/disable").header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        verify(sessions).revokeAllForUser(target.getId());
        mvc.perform(post(path + "/enable").header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post(path + "/unlock").header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$.failedLoginAttempts").value(0));
        target.setFailedLoginAttempts(5);
        mvc.perform(post(path + "/reset-password").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.failedLoginAttempts").value(0))
                .andExpect(content().string(not(containsString(PASSWORD))));
        assertTrue(encoder.matches(PASSWORD, target.getPasswordHash()));
        verify(sessions, times(2)).revokeAllForUser(target.getId());
        var event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(event.capture());
        assertEquals("PASSWORD_RESET", event.getValue().getEventType());
        assertEquals(target.getUuid(), event.getValue().getEntityUuid());
    }

    @Test
    void lastEnabledAdminCannotBeDisabledOrDemotedButAnotherAdminAllowsIt() throws Exception {
        target.setRoles(Set.of(Role.ADMIN));
        when(users.countByEnabledTrueAndRolesContaining(Role.ADMIN)).thenReturn(1L);
        String path = "/api/admin/users/" + target.getUuid();
        String authorization = token(Role.ADMIN);
        mvc.perform(post(path + "/disable").header("Authorization", authorization)).andExpect(status().isConflict());
        mvc.perform(put(path + "/roles").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"VIEWER\"]}"))
                .andExpect(status().isConflict());
        assertTrue(target.isEnabled());
        assertEquals(Set.of(Role.ADMIN), target.getRoles());
        verify(users, never()).saveAndFlush(any());
        when(users.countByEnabledTrueAndRolesContaining(Role.ADMIN)).thenReturn(2L);
        mvc.perform(post(path + "/disable").header("Authorization", authorization)).andExpect(status().isOk());
    }

    @Test
    void duplicatesAndInvalidBodyAreRejectedWithoutSaving() throws Exception {
        String authorization = token(Role.ADMIN);
        when(users.existsByLogin("new-user")).thenReturn(true);
        mvc.perform(post("/api/admin/users").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/users").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace(PASSWORD, "Я".repeat(40)))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + target.getUuid() + "/roles").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").param("size", "101").header("Authorization", authorization))
                .andExpect(status().isBadRequest());
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void errorResponsesHaveSameCorrelationAsResponseHeaderIncludingSecurityAndServerErrors() throws Exception {
        mvc.perform(get("/api/admin/users").header("X-Correlation-Id", "unauthorized-test"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Correlation-Id", "unauthorized-test"))
                .andExpect(jsonPath("$.correlationId").value("unauthorized-test"));
        mvc.perform(get("/api/admin/users").header("Authorization", token(Role.VIEWER)).header("X-Correlation-Id", "forbidden-test"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.correlationId").value("forbidden-test"));
        String authorization = token(Role.ADMIN);
        mvc.perform(get("/api/admin/users").param("page", "-1").header("Authorization", authorization)
                        .header("X-Correlation-Id", "validation-test"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.correlationId").value("validation-test"));
        when(users.findAll(any(Pageable.class))).thenThrow(new DataAccessResourceFailureException("private SQL"));
        mvc.perform(get("/api/admin/users").header("Authorization", authorization).header("X-Correlation-Id", "server-test"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.correlationId").value("server-test"))
                .andExpect(content().string(not(containsString("private SQL"))));
    }

    @Test
    void automaticallyGeneratedCorrelationIsReturnedInHeaderAndError() throws Exception {
        var response = mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized()).andReturn().getResponse();
        String id = response.getHeader("X-Correlation-Id");
        assertNotNull(UUID.fromString(id));
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(id, body.get("correlationId").asText());
    }

    @Test
    void credentialDtoToStringNeverContainsTemporaryPassword() {
        assertFalse(new AdminUserCreateDto("login", "name", Set.of(Role.VIEWER), PASSWORD).toString().contains(PASSWORD));
        assertFalse(new AdminPasswordResetDto(PASSWORD).toString().contains(PASSWORD));
    }
}
