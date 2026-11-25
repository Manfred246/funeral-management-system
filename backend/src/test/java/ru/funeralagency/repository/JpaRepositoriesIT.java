package ru.funeralagency.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.funeralagency.Main;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaRepositoriesIT {
    private DriverManagerDataSource adminSource;
    private DriverManagerDataSource dataSource;
    private ConfigurableApplicationContext context;
    private ClientRepository clients;
    private FuneralRequestRepository requests;
    private String schema;
    private boolean schemaCreated;

    @BeforeAll
    void startOnEmptySchema() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        schema = "repository_test_" + UUID.randomUUID().toString().replace("-", "");
        adminSource = new DriverManagerDataSource(url, username, password);
        try (Connection connection = adminSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            schemaCreated = true;
        }
        String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        dataSource = new DriverManagerDataSource(testUrl, username, password);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT current_schema()")) {
            assertTrue(result.next());
            assertEquals(schema, result.getString(1));
        }
        SpringApplication application = new SpringApplication(Main.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(app -> app.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test-database", Map.of(
                        "spring.datasource.url", testUrl,
                        "spring.datasource.username", username,
                        "spring.datasource.password", password,
                        "spring.flyway.schemas", schema,
                        "spring.flyway.default-schema", schema))));
        // Boot выполняет Flyway перед созданием EntityManagerFactory с validate.
        context = application.run();
        clients = context.getBean(ClientRepository.class);
        requests = context.getBean(FuneralRequestRepository.class);
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(0, clients.count());
        assertEquals(0, requests.count());
    }

    @BeforeEach
    void resetDemoData() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE funeral_requests, clients RESTART IDENTITY");
            statement.execute("DELETE FROM ceremony_types WHERE id = 99");
            statement.execute(Files.readString(Path.of(System.getProperty("project.root"), "database", "seed.sql")));
        }
    }

    @AfterAll
    void removeIsolatedSchema() throws Exception {
        if (context != null) {
            context.close();
        }
        if (schemaCreated && schema.matches("repository_test_[a-f0-9]{32}")) {
            try (Connection connection = adminSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void flywayCreatesV1AndV2AndDoesNotRepeatThem() {
        Flyway flyway = context.getBean(Flyway.class);
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        assertEquals("SQL", flyway.info().current().getType().name());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(6, clients.count());
        assertEquals(12, requests.count());
    }

    @Test
    void lookupIdentitiesContinueAfterFixedIds() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "INSERT INTO ceremony_types (name) VALUES ('Тестовый тип') RETURNING id")) {
                    assertTrue(result.next());
                    assertEquals(3, result.getInt(1));
                }
                try (ResultSet result = statement.executeQuery(
                        "INSERT INTO request_statuses (name) VALUES ('Тестовый статус') RETURNING id")) {
                    assertTrue(result.next());
                    assertEquals(6, result.getInt(1));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026, 1, FR-2026-000001",
            "2026, 15, FR-2026-000015",
            "2026, 123456, FR-2026-123456",
            "2030, 1000000, FR-2030-1000000"
    })
    void databaseGeneratesReadableCodeFromIdAndCreationYear(int year, long id, String expected) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM funeral_requests");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO funeral_requests
                        (id, client_id, deceased_full_name, ceremony_date, ceremony_type_id, status_id, price, created_at)
                    VALUES (?, 1, 'Тест', DATE '2031-01-01', 1, 1, 100, ?)
                    RETURNING request_code
                    """)) {
                insert.setLong(1, id);
                insert.setObject(2, LocalDateTime.of(year, 1, 1, 12, 0));
                try (ResultSet result = insert.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(expected, result.getString(1));
                }
            }
        }
    }

    @Test
    void clientCrudUsesGeneratedIdAndNullableEmail() {
        Client saved = clients.saveAndFlush(new Client(null, "Тестовый клиент", "+79991112233", null));
        assertNotNull(saved.getId());
        assertNull(saved.getEmail());
        assertEquals(saved.getFullName(), clients.findById(saved.getId()).orElseThrow().getFullName());
        saved.setFullName("Изменённый клиент");
        saved.setEmail("test@example.com");
        clients.saveAndFlush(saved);
        assertEquals("test@example.com", clients.findById(saved.getId()).orElseThrow().getEmail());
        clients.deleteById(saved.getId());
        assertTrue(clients.findById(saved.getId()).isEmpty());
    }

    @Test
    void uniqueContactsAndClientForeignKeyAreEnforced() {
        assertThrows(DataIntegrityViolationException.class,
                () -> clients.saveAndFlush(new Client(null, "Другой", "+79990000001", null)));
        assertThrows(DataIntegrityViolationException.class,
                () -> clients.saveAndFlush(new Client(null, "Другой", "+79992223344", "ivanov@example.com")));
        assertTrue(requests.existsByClientId(1));
        assertFalse(requests.existsByClientId(9999));
        assertThrows(DataIntegrityViolationException.class, () -> clients.deleteById(1L));
        assertEquals(6, clients.count());
    }

    @Test
    void requestCrudRoundTripsRelationshipEnumsAndCreationTime() {
        FuneralRequest saved = requests.saveAndFlush(newRequest());
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        var createdAt = saved.getCreatedAt();
        UUID uuid = saved.getUuid();
        String requestCode = saved.getRequestCode();
        assertEquals(String.format(java.util.Locale.ROOT, "FR-%d-%06d",
                createdAt.getYear(), saved.getId()), requestCode);
        assertEquals(0L, saved.getVersion());
        saved.setPrice(new BigDecimal("98765.43"));
        saved.setStatus(RequestStatus.CONFIRMED);
        saved.setCeremonyType(CeremonyType.CREMATION);
        saved.setComment("Изменено");
        saved.setUpdatedAt(createdAt.plusHours(1));
        requests.saveAndFlush(saved);
        FuneralRequest read = requests.findById(saved.getId()).orElseThrow();
        assertEquals(1L, read.getClient().getId());
        assertEquals("+79990000001", read.getClient().getPhone());
        assertEquals(createdAt, read.getCreatedAt());
        assertEquals(saved.getPrice(), read.getPrice());
        assertEquals(CeremonyType.CREMATION, read.getCeremonyType());
        assertEquals(RequestStatus.CONFIRMED, read.getStatus());
        assertEquals(uuid, read.getUuid());
        assertEquals(requestCode, read.getRequestCode());
        assertEquals(createdAt.plusHours(1), read.getUpdatedAt());
        assertEquals(1L, read.getVersion());
        requests.deleteById(saved.getId());
        assertTrue(requests.findById(saved.getId()).isEmpty());
    }

    @Test
    void databaseChecksRejectInvalidValues() {
        FuneralRequest request = newRequest();
        request.setPrice(new BigDecimal("-0.01"));
        assertThrows(DataIntegrityViolationException.class, () -> requests.saveAndFlush(request));
        FuneralRequest blankName = newRequest();
        blankName.setDeceasedFullName("   ");
        assertThrows(DataIntegrityViolationException.class, () -> requests.saveAndFlush(blankName));
    }

    @Test
    void partialNameSearchIsCaseInsensitiveAndEscapesWildcards() {
        assertEquals(3, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("иВаНо").size());
        FuneralRequest request = newRequest();
        request.setDeceasedFullName("O'Brien 50%_!");
        requests.saveAndFlush(request);
        assertEquals(1, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("50%_!").size());
        assertEquals(1, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("%").size());
        assertEquals(1, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("_").size());
        assertEquals(1, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("!").size());
        assertEquals(1, requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("O'Brien").size());
        assertTrue(requests.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("' OR 1=1 --").isEmpty());
    }

    @Test
    void filtersCombineEnumsDatesAndClient() {
        assertEquals(12, requests.findFiltered(null, null, null, null).size());
        assertEquals(2, requests.findByClientIdOrderByIdAsc(2).size());
        assertEquals(1, requests.findFiltered(RequestStatus.NEW, CeremonyType.BURIAL,
                LocalDate.now(), LocalDate.now().plusDays(8)).size());
        assertEquals(3, requests.findFiltered(RequestStatus.NEW, null, null, null).size());
        assertEquals(6, requests.findFiltered(null, CeremonyType.CREMATION, null, null).size());
        assertTrue(requests.findFiltered(null, null, LocalDate.now().plusYears(1), null).isEmpty());
        assertTrue(requests.findFiltered(null, null, null, LocalDate.now().minusYears(1)).isEmpty());
    }

    @Test
    void priceAndDateSortingWorkInBothDirections() {
        for (Sort.Direction direction : Sort.Direction.values()) {
            List<FuneralRequest> prices = requests.findAll(Sort.by(direction, "price").and(Sort.by("id")));
            List<FuneralRequest> dates = requests.findAll(Sort.by(direction, "ceremonyDate").and(Sort.by("id")));
            assertEquals(12, prices.size());
            assertEquals(12, dates.size());
            for (int i = 1; i < prices.size(); i++) {
                int priceOrder = prices.get(i - 1).getPrice().compareTo(prices.get(i).getPrice());
                int dateOrder = dates.get(i - 1).getCeremonyDate().compareTo(dates.get(i).getCeremonyDate());
                assertTrue(direction.isAscending() ? priceOrder <= 0 : priceOrder >= 0);
                assertTrue(direction.isAscending() ? dateOrder <= 0 : dateOrder >= 0);
            }
        }
    }

    @Test
    void unknownLookupIdFailsSafely() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO ceremony_types (id, name) VALUES (99, 'Неизвестный тип')");
            statement.execute("UPDATE funeral_requests SET ceremony_type_id = 99 WHERE id = 1");
        }
        assertThrows(DataAccessException.class, () -> requests.findById(1L));
    }

    private FuneralRequest newRequest() {
        FuneralRequest request = new FuneralRequest(null, clients.findById(1L).orElseThrow(), "Тестовый умерший",
                LocalDate.now().plusDays(5), CeremonyType.BURIAL, RequestStatus.NEW,
                new BigDecimal("12345.67"), LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), null);
        UUID uuid = UUID.randomUUID();
        request.setUuid(uuid);
        request.setPriority(1);
        request.setUpdatedAt(request.getCreatedAt());
        return request;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Для PostgreSQL-тестов задайте " + name);
        }
        return value;
    }
}
