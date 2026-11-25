package ru.funeralagency.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.funeralagency.Main;
import ru.funeralagency.model.User;
import ru.funeralagency.model.Role;
import ru.funeralagency.filter.CorrelationIdFilter;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.funeralagency.repository.FuneralRequestRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationIT {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private DriverManagerDataSource adminSource;
    private DriverManagerDataSource dataSource;
    private ServletWebServerApplicationContext context;
    private String schema;
    private boolean schemaCreated;

    @BeforeAll
    void startApplication() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        schema = "api_test_" + UUID.randomUUID().toString().replace("-", "");
        adminSource = new DriverManagerDataSource(url, username, password);
        try (Connection connection = adminSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            schemaCreated = true;
        }
        String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        dataSource = new DriverManagerDataSource(testUrl, username, password);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT current_schema()")) {
            org.junit.jupiter.api.Assertions.assertTrue(result.next());
            org.junit.jupiter.api.Assertions.assertEquals(schema, result.getString(1));
        }
        SpringApplication application = new SpringApplication(Main.class);
        application.setAdditionalProfiles("test");
        Map<String, Object> settings = Map.of(
                "spring.datasource.url", testUrl,
                "spring.datasource.username", username,
                "spring.datasource.password", password,
                "spring.sql.init.mode", "never",
                "spring.flyway.schemas", schema,
                "spring.flyway.default-schema", schema,
                "server.port", "0"
        );
        application.addInitializers(app -> app.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test-database", settings)));
        context = (ServletWebServerApplicationContext) application.run();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(CorrelationIdFilter.class)).apply(springSecurity()).build();
        objectMapper = context.getBean(ObjectMapper.class);
    }

    @BeforeEach
    void resetTestData() throws Exception {
        // Схему создаёт Flyway при запуске контекста, до Hibernate validate.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE audit_events, request_notes, funeral_request_tags, tags, funeral_requests, clients, refresh_sessions, user_roles, users RESTART IDENTITY");
            Path seed = Path.of(System.getProperty("project.root"), "database", "seed.sql");
            statement.execute(Files.readString(seed));
            statement.execute("DELETE FROM ceremony_types WHERE id NOT IN (1, 2)");
            statement.execute("UPDATE ceremony_types SET enabled = true, name = CASE id WHEN 1 THEN 'Захоронение' ELSE 'Кремация' END");
            statement.execute("ALTER TABLE ceremony_types ALTER COLUMN id RESTART WITH 3");
        }
        User admin = new User();
        admin.setLogin("integration-admin");
        admin.setName("Integration Admin");
        admin.setRoles(java.util.Set.of(Role.ADMIN));
        admin.setPasswordHash(context.getBean(PasswordEncoder.class).encode("integration-password"));
        context.getBean(UserRepository.class).saveAndFlush(admin);
        String token = context.getBean(JwtService.class).createAccessToken(admin);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(CorrelationIdFilter.class)).apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token)).build();
    }

    @AfterAll
    void stopApplication() throws Exception {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (schemaCreated && schema.matches("api_test_[a-f0-9]{32}")) {
                try (Connection connection = adminSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Для PostgreSQL-тестов задайте " + name);
        }
        return value;
    }

    @Test
    void adminUsersTemporaryPasswordResetUnlockAndAuditPersist() throws Exception {
        String created = mockMvc.perform(post("/api/admin/users").header("X-Correlation-Id", "admin-create-it")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"managed-user","name":"Менеджер","roles":["MANAGER"],"temporaryPassword":"temporary-password"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(header().string("X-Correlation-Id", "admin-create-it"))
                .andReturn().getResponse().getContentAsString();
        UUID uuid = UUID.fromString(objectMapper.readTree(created).get("uuid").asText());
        var users = context.getBean(UserRepository.class);
        var stored = users.findByUuid(uuid).orElseThrow();
        assertTrue(context.getBean(PasswordEncoder.class).matches("temporary-password", stored.getPasswordHash()));
        mockMvc.perform(get("/api/admin/users").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"managed-user\",\"password\":\"temporary-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(get("/api/requests").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
        stored.setFailedLoginAttempts(5);
        users.saveAndFlush(stored);
        mockMvc.perform(post("/api/admin/users/" + uuid + "/unlock")).andExpect(status().isOk())
                .andExpect(jsonPath("$.failedLoginAttempts").value(0));
        mockMvc.perform(put("/api/admin/users/" + uuid).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"renamed-user\",\"name\":\"Новое имя\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.login").value("renamed-user"));
        mockMvc.perform(put("/api/admin/users/" + uuid + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"VIEWER\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/" + uuid + "/disable")).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/" + uuid + "/enable")).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/" + uuid + "/reset-password").header("X-Correlation-Id", "reset-it")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"temporaryPassword\":\"reset-temporary-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.failedLoginAttempts").value(0));
        var reset = users.findByUuid(uuid).orElseThrow();
        assertTrue(context.getBean(PasswordEncoder.class).matches("reset-temporary-password", reset.getPasswordHash()));
        var audit = mockMvc.perform(get("/api/admin/audit").param("search", "reset-it").param("sort", "timestamp,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$.content[0].entityUuid").value(uuid.toString()))
                .andExpect(jsonPath("$.content[0].correlationId").value("reset-it"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(audit.contains("reset-temporary-password"));
        assertFalse(audit.contains(reset.getPasswordHash()));
        assertFalse(audit.contains(access));
        mockMvc.perform(get("/api/admin/audit").param("search", "LOGIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(stored.getId()));
    }

    @Test
    void requestAuditLifecycleIsPagedAndSurvivesPermanentDelete() throws Exception {
        String correlation = "request-lifecycle-it";
        String created = mockMvc.perform(post("/api/requests").header("X-Correlation-Id", correlation)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(1, "100", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String uuid = objectMapper.readTree(created).get("uuid").asText();
        String path = "/api/requests/" + uuid;
        mockMvc.perform(put(path).header("X-Correlation-Id", correlation).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(1, "200", "CONFIRMED", 0))).andExpect(status().isOk());
        mockMvc.perform(delete(path).header("X-Correlation-Id", correlation)).andExpect(status().isNoContent());
        mockMvc.perform(post(path + "/restore").header("X-Correlation-Id", correlation)).andExpect(status().isOk());
        mockMvc.perform(delete(path + "/permanent").header("X-Correlation-Id", correlation)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/audit").param("search", correlation).param("sort", "timestamp,asc").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content[0].eventType").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$.content[1].eventType").value("REQUEST_UPDATED"));
        mockMvc.perform(get("/api/admin/audit").param("search", correlation).param("sort", "timestamp,asc")
                        .param("size", "2").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].eventType").value("REQUEST_DELETED"));
    }

    @Test
    void analyticsSummaryUsesRealAggregatesAndLegacyStatisticsStillWorks() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE funeral_requests SET archived = (id IN (1, 2)), urgent = (id IN (2, 3, 4))");
        }
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRequests").value(12))
                .andExpect(jsonPath("$.activeRequests").value(10)).andExpect(jsonPath("$.archivedRequests").value(2))
                .andExpect(jsonPath("$.urgentRequests").value(3)).andExpect(jsonPath("$.averagePrice").value(64250.00))
                .andExpect(jsonPath("$.byCeremonyType.length()").value(2))
                .andExpect(jsonPath("$.byStatus").isNotEmpty())
                .andExpect(jsonPath("$.byPriority[0].priority").value(0))
                .andExpect(jsonPath("$.byPriority[0].count").value(12));
        mockMvc.perform(get("/api/statistics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClients").value(6)).andExpect(jsonPath("$.totalRequests").value(12))
                .andExpect(jsonPath("$.averagePrice").value(64250.00));
    }

    @Test
    void normalRestCreateIgnoresCodeAndTriggerReturnsGeneratedValue() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(requestBody(1, "100", null));
        body.put("requestCode", "CLIENT-SUPPLIED-CODE");
        var result = mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        String expected = String.format(java.util.Locale.ROOT, "FR-%d-%06d",
                java.time.LocalDateTime.parse(created.get("createdAt").asText()).getYear(), created.get("id").asLong());
        assertEquals(expected, created.get("requestCode").asText());
        assertFalse(context.getBean(FuneralRequestRepository.class).findByRequestCode("CLIENT-SUPPLIED-CODE").isPresent());
    }

    @Test
    void csvMultipartPreviewAndApplyAndFilteredDownloadWorkTogether() throws Exception {
        long count = context.getBean(FuneralRequestRepository.class).count();
        byte[] file = ("deceasedFullName,client,ceremonyDate,ceremonyType,status,price\n"
                + "CSV импорт,1," + LocalDate.now().plusDays(5) + ",1,NEW,100\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/requests/import/preview")
                        .file(new MockMultipartFile("file", "requests.csv", "text/csv", file)).param("mode", "CREATE_ONLY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(0));
        assertEquals(count, context.getBean(FuneralRequestRepository.class).count());
        mockMvc.perform(multipart("/api/requests/import/apply")
                        .file(new MockMultipartFile("file", "requests.csv", "text/csv", file)).param("mode", "CREATE_ONLY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(1))
                .andExpect(jsonPath("$.successful[0].requestCode").isNotEmpty()).andExpect(jsonPath("$.errors").isEmpty());
        assertEquals(count + 1, context.getBean(FuneralRequestRepository.class).count());
        byte[] export = mockMvc.perform(get("/api/requests/export/csv").param("q", "CSV импорт").param("minPrice", "100").param("maxPrice", "100"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn().getResponse().getContentAsByteArray();
        var parsed = new ru.funeralagency.service.FuneralRequestCsv().read(export);
        assertNull(parsed.fileError());
        assertEquals(1, parsed.rows().size());
        assertEquals("CSV импорт", parsed.rows().get(0).fields().get("deceasedFullName"));
    }

    @Test
    void bulkArchiveReturnsPartialSuccessAndPersistsBothSuccessfulRecords() throws Exception {
        var repository = context.getBean(FuneralRequestRepository.class);
        var first = repository.findById(1L).orElseThrow();
        var second = repository.findById(2L).orElseThrow();
        UUID unknown = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of("operation", "ARCHIVE",
                "uuids", java.util.List.of(first.getUuid(), unknown, second.getUuid())));
        mockMvc.perform(post("/api/requests/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.successful.length()").value(2))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].uuid").value(unknown.toString()));
        assertTrue(repository.findByUuid(first.getUuid()).orElseThrow().isArchived());
        assertTrue(repository.findByUuid(second.getUuid()).orElseThrow().isArchived());
        assertEquals(first.getVersion() + 1, repository.findByUuid(first.getUuid()).orElseThrow().getVersion());
        assertEquals(second.getVersion() + 1, repository.findByUuid(second.getUuid()).orElseThrow().getVersion());
    }

    @Test
    void notesApiPersistsCurrentAuthorAndRejectsAnotherRequestAndArchivedMutations() throws Exception {
        var requests = context.getBean(FuneralRequestRepository.class);
        var first = requests.findById(1L).orElseThrow();
        var second = requests.findById(2L).orElseThrow();
        var admin = context.getBean(UserRepository.class).findAll().get(0);
        String path = "/api/requests/" + first.getUuid() + "/notes";
        String created = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  Связаться с клиентом  \",\"pinned\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Связаться с клиентом"))
                .andExpect(jsonPath("$.authorUuid").value(admin.getUuid().toString()))
                .andExpect(jsonPath("$.author").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String noteUuid = objectMapper.readTree(created).get("uuid").asText();
        String otherPath = "/api/requests/" + second.getUuid() + "/notes/" + noteUuid;
        mockMvc.perform(put(otherPath).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Чужая\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(otherPath)).andExpect(status().isNotFound());
        mockMvc.perform(put(path + "/" + noteUuid).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  Готово  \",\"pinned\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Готово"))
                .andExpect(jsonPath("$.pinned").value(false));
        mockMvc.perform(delete("/api/requests/" + first.getUuid())).andExpect(status().isNoContent());
        mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("Готово"));
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Новая\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(path + "/" + noteUuid).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Новая\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(path + "/" + noteUuid)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/requests/" + first.getUuid() + "/restore")).andExpect(status().isOk());
        mockMvc.perform(delete(path + "/" + noteUuid)).andExpect(status().isNoContent());
        mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tagApiAndFilteredRequestResponsesWorkWithClosedPersistenceContext() throws Exception {
        String created = mockMvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  VIP  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("VIP"))
                .andReturn().getResponse().getContentAsString();
        long tagId = objectMapper.readTree(created).get("id").asLong();
        var request = context.getBean(FuneralRequestRepository.class).findById(1L).orElseThrow();
        String path = "/api/requests/" + request.getUuid();
        mockMvc.perform(post(path + "/tags/" + tagId)).andExpect(status().isNoContent());
        mockMvc.perform(post(path + "/tags/" + tagId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tags")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(tagId));
        mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].id").value(tagId));
        mockMvc.perform(get(path + "/tags")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("VIP"));
        mockMvc.perform(get("/api/requests").param("tag", Long.toString(tagId)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tags[0].id").value(tagId));
        mockMvc.perform(delete(path + "/tags/" + tagId)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/tags/" + tagId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/requests").param("tag", Long.toString(tagId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/tags/" + tagId)).andExpect(status().isOk());
    }

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
                                  "fullName": "   ",
                                  "phone": "123",
                                  "email": "bad"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "ФИО клиента обязательно"
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
        mockMvc.perform(get("/api/requests/filter")
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

    @Test
    void duplicateFormattedPhoneAndEmailReturnConflict() throws Exception {
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Новый клиент","phone":"+7 (999) 000-00-01","email":null}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Клиент с таким телефоном или email уже существует"));
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Новый клиент","phone":"+79995550000","email":"ivanov@example.com"}
                """))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/clients")).andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void clientUpdateChecksDuplicatesAndKeepsOriginalData() throws Exception {
        mockMvc.perform(put("/api/clients/2").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Другое имя","phone":"+79990000001","email":null}
                """))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/clients/2"))
                .andExpect(jsonPath("$.phone").value("+79990000002"))
                .andExpect(jsonPath("$.fullName").value("Петрова Мария Сергеевна"));
    }

    @Test
    void searchesByClientAndPartialName() throws Exception {
        mockMvc.perform(get("/api/requests/search").param("clientId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clientId").value(2))
                .andExpect(jsonPath("$[1].clientId").value(2));
        mockMvc.perform(get("/api/requests/search").param("deceasedName", "иВаНо"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].deceasedFullName", org.hamcrest.Matchers.containsInAnyOrder(
                        "Иванов Пётр Николаевич", "Иванова Ольга Ивановна", "Орлов Виктор Иванович")));
        mockMvc.perform(get("/api/requests/search").param("clientId", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/requests/search"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/requests/search").param("clientId", "2").param("deceasedName", "Иван"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndSortsThroughPostgres() throws Exception {
        mockMvc.perform(get("/api/requests/filter"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12));
        mockMvc.perform(get("/api/requests/filter").param("status", "NEW")
                        .param("ceremonyType", "BURIAL").param("dateFrom", LocalDate.now().toString())
                        .param("dateTo", LocalDate.now().plusDays(8).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deceasedFullName").value("Иванов Пётр Николаевич"));
        mockMvc.perform(get("/api/requests/sort").param("field", "price").param("ascending", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].price").value(120000.00))
                .andExpect(jsonPath("$[11].price").value(39000.00));
        mockMvc.perform(get("/api/requests/sort").param("field", "ceremonyDate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ceremonyDate")
                        .value(LocalDate.now().minusDays(30).toString()));
        mockMvc.perform(get("/api/requests/sort").param("field", "price; DROP TABLE clients"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uuidLifecyclePreservesIdentityAndIncrementsVersionInPostgres() throws Exception {
        var result = mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(1, "100.1200", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.price").value(100.12))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        String uuid = created.get("uuid").asText();
        String url = "/api/requests/" + uuid;
        assertEquals(url, result.getResponse().getHeader("Location"));
        String createdAt = created.get("createdAt").asText();
        String code = created.get("requestCode").asText();
        FuneralRequestRepository requests = context.getBean(FuneralRequestRepository.class);

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(1, "200.5000", "CONFIRMED", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(200.50))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.uuid").value(uuid))
                .andExpect(jsonPath("$.requestCode").value(code));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(1, "999.00", "CONFIRMED", 0)))
                .andExpect(status().isConflict());
        mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200.50))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(delete(url)).andExpect(status().isNoContent());
        assertTrue(requests.findByUuid(UUID.fromString(uuid)).orElseThrow().isArchived());
        JsonNode archived = objectMapper.readTree(mockMvc.perform(get(url))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn().getResponse().getContentAsString());
        assertTrue(java.time.LocalDateTime.parse(archived.get("updatedAt").asText())
                .isAfter(java.time.LocalDateTime.parse(created.get("updatedAt").asText())));
        mockMvc.perform(get("/api/requests")).andExpect(jsonPath("$.totalElements").value(12));
        mockMvc.perform(delete(url)).andExpect(status().isNoContent());
        mockMvc.perform(get(url)).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.updatedAt").value(archived.get("updatedAt").asText()));

        JsonNode restored = objectMapper.readTree(mockMvc.perform(post(url + "/restore"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.uuid").value(uuid))
                .andExpect(jsonPath("$.requestCode").value(code))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andReturn().getResponse().getContentAsString());
        assertTrue(java.time.LocalDateTime.parse(restored.get("updatedAt").asText())
                .isAfter(java.time.LocalDateTime.parse(archived.get("updatedAt").asText())));
        mockMvc.perform(post(url + "/restore")).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.updatedAt").value(restored.get("updatedAt").asText()));
        mockMvc.perform(get("/api/requests")).andExpect(jsonPath("$.totalElements").value(13));

        mockMvc.perform(delete(url + "/permanent")).andExpect(status().isNoContent());
        assertTrue(requests.findByUuid(UUID.fromString(uuid)).isEmpty());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void invalidPricesAndMissingClientDoNotCreateRows() throws Exception {
        for (String price : new String[]{"100.123", "10000000000.00", "-0.01"}) {
            mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(1, price, null)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(999999, "100.00", null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/requests")).andExpect(jsonPath("$.totalElements").value(12));
    }

    @Test
    void forbiddenTransitionReturnsConflictAndKeepsStatus() throws Exception {
        String response = mockMvc.perform(get("/api/requests/filter").param("status", "NEW"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode request = objectMapper.readTree(response).get(0);
        String uuid = request.get("uuid").asText();
        long clientId = request.get("clientId").asLong();

        mockMvc.perform(put("/api/requests/{uuid}", uuid).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(clientId, "45000", "COMPLETED", request.get("version").asLong())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(get("/api/requests/{uuid}", uuid))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void managedCeremonyTypeAndRequestRelationshipRoundTripThroughPostgres() throws Exception {
        mockMvc.perform(get("/api/ceremony-types")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("BURIAL"))
                .andExpect(jsonPath("$[1].code").value("CREMATION"));
        String typeBody = mockMvc.perform(post("/api/ceremony-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" memorial \",\"name\":\"Прощание\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("MEMORIAL"))
                .andReturn().getResponse().getContentAsString();
        long typeId = objectMapper.readTree(typeBody).get("id").asLong();
        mockMvc.perform(put("/api/ceremony-types/{id}", typeId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новое прощание\",\"code\":\"CHANGED\",\"id\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("MEMORIAL"));
        String requestBody = mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyForType(typeId, 0)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ceremonyType.id").value(typeId))
                .andReturn().getResponse().getContentAsString();
        var created = objectMapper.readTree(requestBody);
        String url = "/api/requests/" + created.get("uuid").asText();
        mockMvc.perform(post("/api/ceremony-types/{id}/disable", typeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyType.name").value("Новое прощание"))
                .andExpect(jsonPath("$.ceremonyType.enabled").value(false));
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(requestBodyForType(typeId, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyForType(typeId, 0))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyForType(999, 0))).andExpect(status().isNotFound());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(requestBodyForType(2, 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ceremonyType.code").value("CREMATION"))
                .andExpect(jsonPath("$.requestCode").value(created.get("requestCode").asText()));
        mockMvc.perform(post("/api/ceremony-types/{id}/enable", typeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyForType(typeId, 0))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/requests").param("ceremonyTypeId", Long.toString(typeId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/requests/filter").param("ceremonyType", "memorial"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/statistics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(14));
    }

    private String requestBodyForType(long typeId, long version) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                requestBody(1, "100.00", "CONFIRMED"));
        body.put("ceremonyTypeId", typeId);
        body.put("version", version);
        return body.toString();
    }

    @Test
    void exportsReadableXlsx() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/requests/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"funeral-requests.xlsx\""))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(13, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("ФИО умершего", workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
        }
    }

    @Test
    void returnsAllRequiredStatistics() throws Exception {
        mockMvc.perform(get("/api/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClients").value(6))
                .andExpect(jsonPath("$.totalRequests").value(12))
                .andExpect(jsonPath("$.newRequests").value(3))
                .andExpect(jsonPath("$.completedRequests").value(2))
                .andExpect(jsonPath("$.cancelledRequests").value(2))
                .andExpect(jsonPath("$.averagePrice").value(64250.00));
    }

    @Test
    void databaseFailureReturnsSafeJson() throws Exception {
        // Меняется только таблица внутри изолированной api_test_... схемы.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE clients RENAME TO clients_unavailable");
            try {
                mockMvc.perform(get("/api/clients"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.message").value("Не удалось выполнить операцию с базой данных"))
                        .andExpect(jsonPath("$.path").value("/api/clients"))
                        .andExpect(jsonPath("$.trace").doesNotExist());
            } finally {
                statement.execute("ALTER TABLE clients_unavailable RENAME TO clients");
            }
        }
    }

    private String requestBody(long clientId, String price, String status) {
        return """
                {"clientId":%d,"deceasedFullName":"Тестовый умерший","ceremonyDate":"%s",
                 "ceremonyTypeId":1,"price":%s,"status":%s,"comment":"Тест"}
                """.formatted(clientId, LocalDate.now().plusDays(7), price,
                status == null ? "null" : "\"" + status + "\"");
    }

    private String updateBody(long clientId, String price, String status, long version) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                requestBody(clientId, price, status));
        body.put("version", version);
        return body.toString();
    }

    private void createRequest(long clientId) throws Exception {
        String request = """
                {
                  "clientId": %d,
                  "deceasedFullName": "Пётр Петров",
                  "ceremonyDate": "%s",
                  "ceremonyTypeId": 1,
                  "price": 10000,
                  "comment": "Тестовая заявка"
                }
                """.formatted(clientId, LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
    }
}
