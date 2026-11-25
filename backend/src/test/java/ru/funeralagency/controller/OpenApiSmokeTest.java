package ru.funeralagency.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.AuditEventRepository;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.RequestNoteRepository;
import ru.funeralagency.repository.TagRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Реальный Spring Context, Springdoc, MVC, Security и Actuator, но без подключения к БД.
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSmokeTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    @MockitoBean private UserRepository users;
    @MockitoBean private RefreshSessionRepository sessions;
    @MockitoBean private FuneralRequestRepository requests;
    @MockitoBean private ClientRepository clients;
    @MockitoBean private CeremonyTypeRepository types;
    @MockitoBean private TagRepository tags;
    @MockitoBean private RequestNoteRepository notes;
    @MockitoBean private AuditEventRepository audit;

    private String token(Role role) {
        User user = new User();
        user.setId(1L);
        user.setLogin("openapi-user");
        user.setName("Документация");
        user.setRoles(Set.of(role));
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.createAccessToken(user);
    }

    private JsonNode document() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void generatedDocumentMatchesEveryActualApiHandlerAndJwtAndSafeDtos() throws Exception {
        JsonNode doc = document();
        assertEquals("bearer", doc.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertTrue(doc.at("/security/0/bearerAuth").isArray());
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String path : mapping.getPatternValues()) {
                if (path.startsWith("/api/")) {
                    for (var method : mapping.getMethodsCondition().getMethods()) {
                        assertTrue(doc.path("paths").path(path).has(method.name().toLowerCase(Locale.ROOT)),
                                () -> "Нет operation для " + method + " " + path);
                    }
                }
            }
        }
        assertFalse(doc.path("paths").has("/actuator/health"));
        assertFalse(doc.path("paths").has("/api/items"));
        for (String name : List.of("User", "FuneralRequest", "Client", "RefreshSession", "RequestNote", "AuditEvent")) {
            assertFalse(doc.path("components").path("schemas").has(name), "Entity schema: " + name);
        }
        assertTrue(doc.at("/components/schemas/ApiErrorResponse/properties/correlationId").isObject());
        for (String path : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")) {
            assertTrue(doc.path("paths").path(path).path("post").path("security").isArray());
            assertTrue(doc.path("paths").path(path).path("post").path("security").isEmpty());
        }
        assertTrue(doc.path("paths").path("/api/auth/me").path("get").path("parameters").isEmpty());
        assertEquals("#/components/schemas/FuneralRequestPageDto", doc.path("paths").path("/api/requests").path("get")
                .path("responses").path("200").path("content").path("*/*").path("schema").path("$ref").asText());
    }

    @Test
    void documentedCreateAndNoContentResponsesAndRequiredFieldsMatchRuntime() throws Exception {
        JsonNode doc = document();
        for (String path : List.of("/api/requests", "/api/clients", "/api/tags", "/api/ceremony-types",
                "/api/admin/users", "/api/requests/{uuid}/notes")) {
            assertTrue(doc.path("paths").path(path).path("post").path("responses").has("201"), path);
            assertFalse(doc.path("paths").path(path).path("post").path("responses").has("200"), path);
        }
        for (String path : List.of("/api/requests/{uuid}", "/api/requests/{uuid}/permanent",
                "/api/requests/{uuid}/tags/{tagId}", "/api/requests/{uuid}/notes/{noteUuid}", "/api/clients/{id}")) {
            assertTrue(doc.path("paths").path(path).path("delete").path("responses").has("204"), path);
        }
        assertTrue(doc.path("paths").path("/api/auth/logout").path("post").path("responses").has("204"));
        Set<String> required = new HashSet<>();
        doc.at("/components/schemas/FuneralRequestCreateDto/required").forEach(value -> required.add(value.asText()));
        assertEquals(Set.of("clientId", "deceasedFullName", "ceremonyDate", "ceremonyTypeId", "price"), required);
        assertFalse(doc.at("/components/schemas/FuneralRequestCreateDto/properties").has("requestCode"));
        assertFalse(doc.at("/components/schemas/RequestNoteWriteDto/properties").has("author"));
        assertTrue(doc.at("/components/schemas/AdminUserCreateDto/properties/temporaryPassword/writeOnly").asBoolean());
        assertFalse(doc.at("/components/schemas/AdminUserDto/properties").has("passwordHash"));
    }

    @Test
    void generatedQueryParamsBinaryDownloadsAndMultipartMatchActualControllers() throws Exception {
        JsonNode doc = document();
        assertEquals(Set.of("q", "client", "ceremonyTypeId", "status", "urgent", "priority", "tag", "archived", "minPrice", "maxPrice", "page", "size", "sort"),
                parameterNames(doc.path("paths").path("/api/requests").path("get")));
        assertEquals(Set.of("q", "client", "ceremonyTypeId", "status", "urgent", "priority", "tag", "archived", "minPrice", "maxPrice", "sort"),
                parameterNames(doc.path("paths").path("/api/requests/export/csv").path("get")));
        var csv = doc.path("paths").path("/api/requests/export/csv").path("get").path("responses").path("200");
        assertEquals("binary", csv.path("content").path("text/csv").path("schema").path("format").asText());
        assertTrue(doc.path("paths").path("/api/requests/export").path("get").path("responses").path("200")
                .path("content").has("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(doc.path("paths").path("/api/requests/import/preview").path("post").path("requestBody")
                .path("content").has("multipart/form-data"));
    }

    private Set<String> parameterNames(JsonNode operation) {
        Set<String> names = new HashSet<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        return names;
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,/v3/api-docs", "MANAGER,/v3/api-docs", "VIEWER,/swagger-ui/index.html", "MANAGER,/swagger-ui/index.html",
            "VIEWER,/swagger-ui/swagger-ui.css", "MANAGER,/v3/api-docs/swagger-config", "VIEWER,/v3/api-docs.yaml"})
    void documentationIsAdminOnly(Role role, String path) throws Exception {
        mvc.perform(get(path).header("Authorization", token(role))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/v3/api-docs.yaml"})
    void docsRequireJwtFromFirstRequest(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"})
    void adminCanReadActualSwaggerAssetsAndDocuments(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", token(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void healthIsPublicAndDoesNotExposeComponentsOrDetailsOrOtherActuatorEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(get("/actuator/env").header("Authorization", token(Role.ADMIN))).andExpect(status().isForbidden());
    }
}
