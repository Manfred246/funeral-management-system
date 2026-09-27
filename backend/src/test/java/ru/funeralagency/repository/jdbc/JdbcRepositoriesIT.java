package ru.funeralagency.repository.jdbc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.funeralagency.Main;
import ru.funeralagency.exception.DatabaseException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Реальный PostgreSQL. Все изменения происходят только в новой тестовой схеме. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcRepositoriesIT {
    private DriverManagerDataSource adminSource;
    private DriverManagerDataSource dataSource;
    private ClientRepository clients;
    private FuneralRequestRepository requests;
    private String schema;
    private String testUrl;
    private String username;
    private String password;
    private boolean schemaCreated;

    @BeforeAll
    void createTestSchema() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        username = requiredEnvironment("TEST_DB_USERNAME");
        password = requiredEnvironment("TEST_DB_PASSWORD");
        schema = "repository_test_" + UUID.randomUUID().toString().replace("-", "");
        adminSource = new DriverManagerDataSource(url, username, password);
        try (Connection connection = adminSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            schemaCreated = true;
        }
        String separator = url.contains("?") ? "&" : "?";
        testUrl = url + separator + "currentSchema=" + schema;
        dataSource = new DriverManagerDataSource(testUrl, username, password);
        // Fail before executing drop.sql if the driver did not select the isolated schema.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT current_schema()")) {
            assertTrue(result.next());
            assertEquals(schema, result.getString(1));
        }
        clients = new JdbcClientRepository(dataSource);
        requests = new JdbcFuneralRequestRepository(dataSource);
    }

    @BeforeEach
    void resetTestData() throws Exception {
        runScript("drop.sql");
        runScript("schema.sql");
        runScript("seed.sql");
    }

    @AfterAll
    void removeTestSchema() throws Exception {
        if (schemaCreated && schema.matches("repository_test_[a-f0-9]{32}")) {
            try (Connection connection = adminSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void scriptsCreateRequiredDemoData() {
        assertEquals(6, clients.findAll().size());
        assertEquals(12, requests.findAll().size());
        for (RequestStatus status : RequestStatus.values()) {
            assertFalse(requests.findByStatus(status).isEmpty());
        }
        for (CeremonyType type : CeremonyType.values()) {
            assertEquals(6, requests.findByCeremonyType(type).size());
        }
    }

    @Test
    void seedRefusesToDuplicateExistingData() {
        assertThrows(Exception.class, () -> runScript("seed.sql"));
        assertEquals(6, clients.findAll().size());
        assertEquals(12, requests.findAll().size());
    }

    @Test
    void clientCrudHandlesGeneratedIdAndNullableEmail() {
        Client saved = clients.create(new Client(null, "Тестовый клиент", "+79991112233", null));
        assertNotNull(saved.getId());
        assertNull(saved.getEmail());
        assertTrue(clients.existsById(saved.getId()));
        assertClientFieldsEqual(saved, clients.findById(saved.getId()).orElseThrow());
        saved.setFullName("Изменённый клиент");
        saved.setEmail("test@example.com");
        assertClientFieldsEqual(saved, clients.update(saved));
        assertClientFieldsEqual(saved, clients.findById(saved.getId()).orElseThrow());
        clients.deleteById(saved.getId());
        assertFalse(clients.existsById(saved.getId()));
        assertTrue(clients.findById(saved.getId()).isEmpty());
    }

    @Test
    void duplicateClientPhoneIsRejected() {
        DatabaseException error = assertThrows(DatabaseException.class,
                () -> clients.create(new Client(null, "Другой клиент", "+79990000001", null)));
        assertEquals("23505", error.getSqlState());
        assertEquals(6, clients.findAll().size());
    }

    @Test
    void duplicateClientEmailIsRejected() {
        DatabaseException error = assertThrows(DatabaseException.class,
                () -> clients.create(new Client(null, "Другой клиент", "+79992223344", "ivanov@example.com")));
        assertEquals("23505", error.getSqlState());
    }

    @Test
    void missingUpdatesAreReportedAndMissingDeletesAreHarmless() {
        Client missing = new Client(9999L, "Нет клиента", "+79992223344", null);
        assertThrows(EntityNotFoundException.class, () -> clients.update(missing));
        FuneralRequest request = newRequest();
        request.setId(9999L);
        request.setStatus(RequestStatus.NEW);
        assertThrows(EntityNotFoundException.class, () -> requests.update(request));
        clients.deleteById(9999);
        requests.deleteById(9999);
        assertTrue(requests.findById(9999).isEmpty());
    }

    @Test
    void requestCrudPreservesCreationTime() {
        FuneralRequest input = newRequest();
        input.setId(9999L);
        input.setCreatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        FuneralRequest saved = requests.create(input);
        assertNotEquals(9999L, saved.getId());
        assertEquals(RequestStatus.NEW, saved.getStatus());
        assertNotEquals(input.getCreatedAt(), saved.getCreatedAt());
        assertRequestFieldsEqual(saved, requests.findById(saved.getId()).orElseThrow());
        LocalDateTime creationTime = saved.getCreatedAt();
        saved.setPrice(new BigDecimal("98765.43"));
        saved.setStatus(RequestStatus.CONFIRMED);
        saved.setCeremonyType(CeremonyType.CREMATION);
        saved.setComment("Изменённый комментарий");
        saved.setCreatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        FuneralRequest updated = requests.update(saved);
        assertEquals(creationTime, updated.getCreatedAt());
        assertEquals(saved.getPrice(), updated.getPrice());
        assertEquals(CeremonyType.CREMATION, updated.getCeremonyType());
        assertEquals(RequestStatus.CONFIRMED, updated.getStatus());
        assertRequestFieldsEqual(updated, requests.findById(saved.getId()).orElseThrow());
        requests.deleteById(saved.getId());
        assertTrue(requests.findById(saved.getId()).isEmpty());
    }

    @Test
    void missingClientIsRejectedByForeignKey() {
        FuneralRequest request = newRequest();
        request.setClientId(9999L);
        DatabaseException error = assertThrows(DatabaseException.class, () -> requests.create(request));
        assertEquals("23503", error.getSqlState());
    }

    @Test
    void clientWithRequestsCannotBeDeleted() {
        assertTrue(requests.existsByClientId(1));
        assertFalse(requests.existsByClientId(9999));
        DatabaseException error = assertThrows(DatabaseException.class, () -> clients.deleteById(1));
        assertTrue(List.of("23503", "23001").contains(error.getSqlState()));
        assertTrue(clients.existsById(1));
        assertEquals(12, requests.findAll().size());
    }

    @Test
    void invalidValuesAreRejectedByChecks() {
        FuneralRequest request = newRequest();
        request.setPrice(new BigDecimal("-0.01"));
        assertEquals("23514", assertThrows(DatabaseException.class, () -> requests.create(request)).getSqlState());
        request.setPrice(BigDecimal.ZERO);
        request.setDeceasedFullName("   ");
        assertEquals("23514", assertThrows(DatabaseException.class, () -> requests.create(request)).getSqlState());
        request.setDeceasedFullName("Корректное имя");
        request.setCeremonyType(null);
        assertEquals("23502", assertThrows(DatabaseException.class, () -> requests.create(request)).getSqlState());
    }

    @Test
    void nameSearchIsPartialAndCaseInsensitive() {
        // Две фамилии Иванов/Иванова и отчество Иванович у Орлова.
        assertEquals(3, requests.findByDeceasedName("иВаНо").size());
        assertTrue(requests.findByDeceasedName("Несуществующее имя").isEmpty());
    }

    @Test
    void nameSearchTreatsWildcardsAndSqlAsText() {
        FuneralRequest request = newRequest();
        request.setDeceasedFullName("O'Brien 50%_!");
        requests.create(request);
        assertEquals(1, requests.findByDeceasedName("50%_!").size());
        assertEquals(1, requests.findByDeceasedName("O'Brien").size());
        assertTrue(requests.findByDeceasedName("' OR 1=1 --").isEmpty());
        assertEquals(13, requests.findAll().size());
    }

    @Test
    void clientSearchAndFiltersReturnOnlyMatchingRows() {
        List<FuneralRequest> clientRequests = requests.findByClientId(2);
        assertEquals(2, clientRequests.size());
        for (FuneralRequest request : clientRequests) {
            assertEquals(2L, request.getClientId());
        }
        for (FuneralRequest request : requests.findByStatus(RequestStatus.COMPLETED)) {
            assertEquals(RequestStatus.COMPLETED, request.getStatus());
        }
        for (FuneralRequest request : requests.findByCeremonyType(CeremonyType.CREMATION)) {
            assertEquals(CeremonyType.CREMATION, request.getCeremonyType());
        }
    }

    @Test
    void priceSortingWorksInBothDirections() {
        List<FuneralRequest> ascending = requests.findAllOrderByPrice(true);
        List<FuneralRequest> descending = requests.findAllOrderByPrice(false);
        assertEquals(12, ascending.size());
        assertEquals(12, descending.size());
        for (int i = 1; i < ascending.size(); i++) {
            assertTrue(ascending.get(i - 1).getPrice().compareTo(ascending.get(i).getPrice()) <= 0);
            assertTrue(descending.get(i - 1).getPrice().compareTo(descending.get(i).getPrice()) >= 0);
        }
    }

    @Test
    void dateSortingWorksInBothDirections() {
        List<FuneralRequest> ascending = requests.findAllOrderByCeremonyDate(true);
        List<FuneralRequest> descending = requests.findAllOrderByCeremonyDate(false);
        assertEquals(12, ascending.size());
        assertEquals(12, descending.size());
        for (int i = 1; i < ascending.size(); i++) {
            assertFalse(ascending.get(i - 1).getCeremonyDate().isAfter(ascending.get(i).getCeremonyDate()));
            assertFalse(descending.get(i - 1).getCeremonyDate().isBefore(descending.get(i).getCeremonyDate()));
        }
    }

    @Test
    void unknownLookupIdProducesDatabaseException() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO ceremony_types (id, name) VALUES (99, 'Неизвестный тип')");
            statement.executeUpdate("UPDATE funeral_requests SET ceremony_type_id = 99 WHERE id = 1");
        }
        DatabaseException error = assertThrows(DatabaseException.class, () -> requests.findById(1));
        assertEquals("22000", error.getSqlState());
    }

    @Test
    void springBootStartsAndWiresRepositoriesToPostgres() throws Exception {
        SpringApplication application = new SpringApplication(Main.class);
        Map<String, Object> settings = Map.of(
                "spring.datasource.url", testUrl,
                "spring.datasource.username", username,
                "spring.datasource.password", password,
                "spring.sql.init.mode", "never",
                "server.port", "0"
        );
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test-database", settings)));
        try (ConfigurableApplicationContext context = application.run()) {
            assertEquals(6, context.getBean(ClientRepository.class).findAll().size());
            assertEquals(12, context.getBean(FuneralRequestRepository.class).findAll().size());
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            try (HttpClient client = HttpClient.newHttpClient()) {
                assertEquals(404, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            }
        }
    }

    private FuneralRequest newRequest() {
        return new FuneralRequest(null, 1L, "Тестовый умерший", LocalDate.now().plusDays(5),
                CeremonyType.BURIAL, null, new BigDecimal("12345.67"), null, null);
    }

    private void assertClientFieldsEqual(Client expected, Client actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getFullName(), actual.getFullName());
        assertEquals(expected.getPhone(), actual.getPhone());
        assertEquals(expected.getEmail(), actual.getEmail());
    }

    private void assertRequestFieldsEqual(FuneralRequest expected, FuneralRequest actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getClientId(), actual.getClientId());
        assertEquals(expected.getDeceasedFullName(), actual.getDeceasedFullName());
        assertEquals(expected.getCeremonyDate(), actual.getCeremonyDate());
        assertEquals(expected.getCeremonyType(), actual.getCeremonyType());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getPrice(), actual.getPrice());
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
        assertEquals(expected.getComment(), actual.getComment());
    }

    private void runScript(String filename) throws Exception {
        Path script = Path.of(System.getProperty("project.root"), "database", filename);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(script));
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Для PostgreSQL-тестов задайте " + name);
        }
        return value;
    }
}
