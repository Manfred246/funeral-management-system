package ru.funeralagency.repository;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.funeralagency.Main;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.service.AuthService;
import ru.funeralagency.service.TagService;
import ru.funeralagency.model.Tag;
import ru.funeralagency.exception.BusinessException;
import org.hibernate.SessionFactory;
import ru.funeralagency.model.Role;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.funeralagency.dto.FuneralRequestFilterDto;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaRepositoriesIT {
    private DriverManagerDataSource adminSource;
    private DriverManagerDataSource dataSource;
    private ConfigurableApplicationContext context;
    private ClientRepository clients;
    private FuneralRequestRepository requests;
    private FuneralRequestService requestService;
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
        application.setAdditionalProfiles("test");
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
        requestService = context.getBean(FuneralRequestService.class);
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(0, clients.count());
        assertEquals(0, requests.count());
    }

    @BeforeEach
    void resetDemoData() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE funeral_request_tags, tags, funeral_requests, clients, refresh_sessions, user_roles, users RESTART IDENTITY");
            statement.execute("DELETE FROM ceremony_types WHERE id NOT IN (1, 2)");
            statement.execute("UPDATE ceremony_types SET enabled = true, name = CASE id WHEN 1 THEN 'Захоронение' ELSE 'Кремация' END");
            statement.execute("ALTER TABLE ceremony_types ALTER COLUMN id RESTART WITH 3");
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
    void flywayCreatesV1ThroughV5AndDoesNotRepeatThem() {
        Flyway flyway = context.getBean(Flyway.class);
        assertEquals("5", flyway.info().current().getVersion().getVersion());
        assertEquals("SQL", flyway.info().current().getType().name());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(6, clients.count());
        assertEquals(12, requests.count());
    }

    @Test
    void failedLoginCounterCommitsEvenWhenAuthServiceThrows() {
        AuthService auth = context.getBean(AuthService.class);
        UserRepository users = context.getBean(UserRepository.class);
        auth.createInitialAdmin("auth-test", "temporary-password", "Тест");
        var user = users.findAll().get(0);
        assertEquals(java.util.Set.of(Role.ADMIN), users.findByUuid(user.getUuid()).orElseThrow().getRoles());
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> auth.login("auth-test", "wrong-password"));
            assertEquals(attempt, users.findByUuid(user.getUuid()).orElseThrow().getFailedLoginAttempts());
        }
        assertThrows(BadCredentialsException.class, () -> auth.login("auth-test", "temporary-password"));
        assertEquals(0, context.getBean(RefreshSessionRepository.class).count());
    }

    @Test
    void refreshLogoutAndPasswordChangePersistInPostgres() {
        AuthService auth = context.getBean(AuthService.class);
        UserRepository users = context.getBean(UserRepository.class);
        RefreshSessionRepository sessions = context.getBean(RefreshSessionRepository.class);
        auth.createInitialAdmin("auth-test", "temporary-password", "Тест");
        var tokens = auth.login("auth-test", "temporary-password");
        var stored = sessions.findAll().get(0);
        assertNotEquals(tokens.refreshToken(), stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(tokens.refreshToken(), auth.refresh(tokens.refreshToken()).refreshToken());
        auth.logout(tokens.refreshToken());
        assertTrue(sessions.findById(stored.getId()).orElseThrow().isRevoked());
        assertThrows(BadCredentialsException.class, () -> auth.refresh(tokens.refreshToken()));

        var secondLogin = auth.login("auth-test", "temporary-password");
        auth.changePassword(tokens.user().uuid(), "temporary-password", "changed-password");
        var changed = users.findByUuid(tokens.user().uuid()).orElseThrow();
        assertFalse(changed.isPasswordChangeRequired());
        assertTrue(context.getBean(PasswordEncoder.class).matches("changed-password", changed.getPasswordHash()));
        assertThrows(BadCredentialsException.class, () -> auth.refresh(secondLogin.refreshToken()));
        assertThrows(BadCredentialsException.class, () -> auth.login("auth-test", "temporary-password"));
        assertNotNull(auth.login("auth-test", "changed-password").accessToken());
    }

    @Test
    void lookupIdentitiesContinueAfterFixedIds() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "INSERT INTO ceremony_types (code, name) VALUES ('TEST_TYPE', 'Тестовый тип') RETURNING id")) {
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
        saved.setCeremonyType(new CeremonyType(2L, "CREMATION", "Кремация", true));
        saved.setComment("Изменено");
        saved.setUpdatedAt(createdAt.plusHours(1));
        requests.saveAndFlush(saved);
        FuneralRequest read = requests.findById(saved.getId()).orElseThrow();
        assertEquals(1L, read.getClient().getId());
        assertEquals("+79990000001", read.getClient().getPhone());
        assertEquals(createdAt, read.getCreatedAt());
        assertEquals(saved.getPrice(), read.getPrice());
        assertEquals(2L, read.getCeremonyType().getId());
        assertEquals("CREMATION", read.getCeremonyType().getCode());
        assertEquals(RequestStatus.CONFIRMED, read.getStatus());
        assertEquals(uuid, read.getUuid());
        assertEquals(requestCode, read.getRequestCode());
        assertEquals(createdAt.plusHours(1), read.getUpdatedAt());
        assertEquals(1L, read.getVersion());
        requests.deleteById(saved.getId());
        assertTrue(requests.findById(saved.getId()).isEmpty());
    }

    @Test
    void overlappingTransactionsRejectStaleVersionInsteadOfLosingUpdate() {
        FuneralRequest saved = requests.saveAndFlush(newRequest());
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        var first = factory.createEntityManager();
        var second = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            FuneralRequest firstCopy = first.find(FuneralRequest.class, saved.getId());
            FuneralRequest secondCopy = second.find(FuneralRequest.class, saved.getId());
            assertEquals(firstCopy.getVersion(), secondCopy.getVersion());

            firstCopy.setComment("Первое изменение");
            first.getTransaction().commit();
            secondCopy.setComment("Второе изменение");
            assertThrows(OptimisticLockException.class, second::flush);

            FuneralRequest read = requests.findByUuid(saved.getUuid()).orElseThrow();
            assertEquals("Первое изменение", read.getComment());
            assertEquals(saved.getVersion() + 1, read.getVersion());
            assertEquals(saved.getRequestCode(), read.getRequestCode());
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
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
        assertEquals(2, requestService.search("иВаНо", null).size());
        FuneralRequest request = newRequest();
        request.setDeceasedFullName("O'Brien 50%_!");
        requests.saveAndFlush(request);
        assertEquals(1, requestService.search("50%_!", null).size());
        assertEquals(1, requestService.search("%", null).size());
        assertEquals(1, requestService.search("_", null).size());
        assertEquals(1, requestService.search("!", null).size());
        assertEquals(1, requestService.search("O'Brien", null).size());
        assertTrue(requestService.search("' OR 1=1 --", null).isEmpty());
    }

    @Test
    void filtersCombineEnumsDatesAndClient() {
        assertEquals(12, requestService.filter(null, null, null, null).size());
        assertEquals(2, requestService.search(null, 2L).size());
        assertEquals(1, requestService.filter(RequestStatus.NEW, "BURIAL",
                LocalDate.now(), LocalDate.now().plusDays(8)).size());
        assertEquals(3, requestService.filter(RequestStatus.NEW, null, null, null).size());
        assertEquals(6, requestService.filter(null, "CREMATION", null, null).size());
        assertTrue(requestService.filter(null, null, LocalDate.now().plusYears(1), null).isEmpty());
        assertTrue(requestService.filter(null, null, null, LocalDate.now().minusYears(1)).isEmpty());
    }

    @Test
    void priceAndDateSortingWorkInBothDirections() {
        for (Sort.Direction direction : Sort.Direction.values()) {
            List<FuneralRequest> prices = requestService.sort("price", direction == Sort.Direction.ASC);
            List<FuneralRequest> dates = requestService.sort("ceremonyDate", direction == Sort.Direction.ASC);
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
    void combinedPagedSpecificationAndExactRequestCodeWorkInPostgres() throws Exception {
        FuneralRequest first = queryFixtures().get(0);
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setQ(first.getRequestCode().toLowerCase(java.util.Locale.ROOT));
        filter.setClient(1L);
        filter.setCeremonyTypeId(1L);
        filter.setStatus(RequestStatus.NEW);
        filter.setUrgent(true);
        filter.setPriority(4);
        filter.setMinPrice(new BigDecimal("100.00"));
        filter.setMaxPrice(new BigDecimal("100.00"));

        var result = requestService.findPage(filter, 0, 20, "price,asc");
        assertEquals(1, result.totalElements());
        assertEquals(first.getUuid(), result.content().get(0).getUuid());
        assertEquals(4, result.content().get(0).getPriority());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pageFilterCases")
    void pageFiltersExecuteInPostgres(String name, Consumer<FuneralRequestFilterDto> configure, long count)
            throws Exception {
        queryFixtures();
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        configure.accept(filter);
        assertEquals(count, requestService.findPage(filter, 0, 20, "price,asc").totalElements());
    }

    static Stream<Arguments> pageFilterCases() {
        return Stream.of(
                filterCase("default active", f -> {}, 2),
                filterCase("archived", f -> f.setArchived(true), 1),
                filterCase("deceased name", f -> f.setQ("иВаНоВ"), 1),
                filterCase("request code", f -> f.setQ("fr-2026"), 2),
                filterCase("client name", f -> f.setQ("мария"), 1),
                filterCase("literal wildcards", f -> f.setQ("50%_!"), 1),
                filterCase("literal percent", f -> f.setQ("%"), 1),
                filterCase("literal underscore", f -> f.setQ("_"), 1),
                filterCase("literal escape", f -> f.setQ("!"), 1),
                filterCase("apostrophe", f -> f.setQ("O'Brien"), 1),
                filterCase("SQL text is data", f -> f.setQ("' OR 1=1 --"), 0),
                filterCase("blank q", f -> f.setQ("   "), 2),
                filterCase("client", f -> f.setClient(2L), 1),
                filterCase("ceremonyType", f -> f.setCeremonyTypeId(2L), 1),
                filterCase("status", f -> f.setStatus(RequestStatus.CONFIRMED), 1),
                filterCase("urgent true", f -> f.setUrgent(true), 1),
                filterCase("urgent false", f -> f.setUrgent(false), 1),
                filterCase("priority", f -> f.setPriority(4), 1),
                filterCase("min inclusive", f -> f.setMinPrice(new BigDecimal("100")), 2),
                filterCase("max inclusive", f -> f.setMaxPrice(new BigDecimal("100")), 1),
                filterCase("price range", f -> {
                    f.setMinPrice(new BigDecimal("100"));
                    f.setMaxPrice(new BigDecimal("200"));
                }, 2));
    }

    private static Arguments filterCase(String name, Consumer<FuneralRequestFilterDto> configure, long count) {
        return Arguments.of(name, configure, count);
    }

    @Test
    void paginationAndBothSortDirectionsUseOnlyActiveRows() throws Exception {
        List<FuneralRequest> records = queryFixtures();
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        var ascending = requestService.findPage(filter, 0, 1, "price,asc");
        var descending = requestService.findPage(filter, 0, 1, "price,desc");
        assertEquals(records.get(0).getUuid(), ascending.content().get(0).getUuid());
        assertEquals(records.get(1).getUuid(), descending.content().get(0).getUuid());
        assertEquals(2, ascending.totalElements());
        assertEquals(2, ascending.totalPages());
        assertEquals(records.get(1).getUuid(), requestService.findPage(filter, 1, 1, "price,asc")
                .content().get(0).getUuid());
        assertTrue(requestService.findPage(filter, 2, 1, "price,asc").content().isEmpty());

        // Одинаковый createdAt у двух строк: дополнительный ID фиксирует порядок страниц.
        assertEquals(records.get(0).getUuid(), requestService.findPage(filter, 0, 1, "createdAt,desc")
                .content().get(0).getUuid());
        assertEquals(records.get(1).getUuid(), requestService.findPage(filter, 1, 1, "createdAt,desc")
                .content().get(0).getUuid());
    }

    @Test
    void legacyWrappersAlsoExcludeArchivedRows() throws Exception {
        queryFixtures();
        assertEquals(1, requestService.search(null, 1L).size());
        assertTrue(requestService.search("Архивный", null).isEmpty());
        assertEquals(2, requestService.filter(null, null, null, null).size());
        assertEquals(2, requestService.sort("price", true).size());
        assertEquals(3, requests.findAllByOrderByIdAsc().size());
    }

    private List<FuneralRequest> queryFixtures() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM funeral_requests");
        }
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        FuneralRequest first = newRequest();
        first.setDeceasedFullName("Иванов 50%_! O'Brien");
        first.setCreatedAt(createdAt);
        first.setUpdatedAt(createdAt);
        first.setCeremonyDate(createdAt.toLocalDate().plusDays(2));
        first.setPrice(new BigDecimal("100.00"));
        first.setUrgent(true);
        first.setPriority(4);
        FuneralRequest second = newRequest();
        second.setDeceasedFullName("Петров");
        second.setClient(clients.findById(2L).orElseThrow());
        second.setCreatedAt(createdAt);
        second.setUpdatedAt(createdAt);
        second.setCeremonyDate(createdAt.toLocalDate().plusDays(4));
        second.setCeremonyType(new CeremonyType(2L, "CREMATION", "Кремация", true));
        second.setStatus(RequestStatus.CONFIRMED);
        second.setPrice(new BigDecimal("200.00"));
        FuneralRequest archived = newRequest();
        archived.setDeceasedFullName("Архивный");
        archived.setPrice(new BigDecimal("300.00"));
        archived.setArchived(true);
        return requests.saveAllAndFlush(List.of(first, second, archived));
    }

    @Test
    void tagsChangeVersionOnlyOnActualMutationAndPermanentDeleteKeepsTag() throws Exception {
        TagService tagService = context.getBean(TagService.class);
        Tag tag = tagService.create("  VIP  ");
        assertEquals("VIP", tag.getName());
        assertThrows(BusinessException.class, () -> tagService.create("vip"));
        FuneralRequest saved = requests.saveAndFlush(newRequest());
        UUID uuid = saved.getUuid();
        Long version = saved.getVersion();
        String code = saved.getRequestCode();
        LocalDateTime createdAt = saved.getCreatedAt();

        requestService.addTagByUuid(uuid, tag.getId());
        FuneralRequest added = requests.findByUuid(uuid).orElseThrow();
        assertEquals(version + 1, added.getVersion());
        assertEquals(1, added.getTags().size());
        assertEquals(uuid, added.getUuid());
        assertEquals(code, added.getRequestCode());
        assertEquals(createdAt, added.getCreatedAt());
        requestService.addTagByUuid(uuid, tag.getId());
        FuneralRequest repeated = requests.findByUuid(uuid).orElseThrow();
        assertEquals(added.getVersion(), repeated.getVersion());
        assertEquals(added.getUpdatedAt(), repeated.getUpdatedAt());

        requestService.removeTagByUuid(uuid, tag.getId());
        FuneralRequest removed = requests.findByUuid(uuid).orElseThrow();
        assertEquals(version + 2, removed.getVersion());
        assertTrue(removed.getTags().isEmpty());
        assertNotNull(tagService.getById(tag.getId()));
        requestService.removeTagByUuid(uuid, tag.getId());
        FuneralRequest removedAgain = requests.findByUuid(uuid).orElseThrow();
        assertEquals(removed.getVersion(), removedAgain.getVersion());
        assertEquals(removed.getUpdatedAt(), removedAgain.getUpdatedAt());

        requestService.addTagByUuid(uuid, tag.getId());
        requestService.archiveByUuid(uuid);
        assertEquals(tag.getId(), requestService.getTagsByUuid(uuid).get(0).id());
        assertThrows(BusinessException.class, () -> requestService.addTagByUuid(uuid, tag.getId()));
        assertThrows(BusinessException.class, () -> requestService.removeTagByUuid(uuid, tag.getId()));
        requestService.deletePermanentlyByUuid(uuid);
        assertTrue(requests.findByUuid(uuid).isEmpty());
        assertEquals(1, context.getBean(TagRepository.class).count());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM funeral_request_tags")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void tagFilterCombinesStatusArchiveAndPaginationWithoutDuplicates() throws Exception {
        List<FuneralRequest> records = queryFixtures();
        Tag tag = context.getBean(TagService.class).create("VIP");
        Tag other = context.getBean(TagService.class).create("Транспорт");
        for (int index = 0; index < 2; index++) {
            requestService.addTagByUuid(records.get(index).getUuid(), tag.getId());
        }
        requestService.addTagByUuid(records.get(0).getUuid(), other.getId());
        // Архивирование выполняется уже после назначения тега.
        requestService.restoreByUuid(records.get(2).getUuid());
        requestService.addTagByUuid(records.get(2).getUuid(), tag.getId());
        requestService.archiveByUuid(records.get(2).getUuid());
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setTag(tag.getId());
        var first = requestService.findPage(filter, 0, 1, "price,asc");
        var second = requestService.findPage(filter, 1, 1, "price,asc");
        assertEquals(2, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(records.get(0).getUuid(), first.content().get(0).getUuid());
        assertEquals(2, first.content().get(0).getTags().size());
        assertEquals(records.get(1).getUuid(), second.content().get(0).getUuid());
        assertTrue(requestService.findPage(filter, 2, 1, "price,asc").content().isEmpty());
        filter.setStatus(RequestStatus.CONFIRMED);
        assertEquals(records.get(1).getUuid(), requestService.findPage(filter, 0, 20, "price,asc")
                .content().get(0).getUuid());
        assertEquals(1, requestService.findPage(filter, 0, 20, "price,asc").totalElements());
        filter.setStatus(null);
        filter.setArchived(true);
        assertEquals(1, requestService.findPage(filter, 0, 20, "price,asc").totalElements());
        assertEquals(records.get(2).getUuid(), requestService.findPage(filter, 0, 20, "price,asc")
                .content().get(0).getUuid());
        filter.setTag(Long.MAX_VALUE);
        assertEquals(0, requestService.findPage(filter, 0, 20, "price,asc").totalElements());
    }

    @Test
    void pageLoadsTagsInOneBatchWithoutCollectionFetchPagination() {
        Tag tag = context.getBean(TagService.class).create("VIP");
        List<FuneralRequest> records = requests.findAllByOrderByIdAsc();
        for (FuneralRequest request : records) {
            requestService.addTagByUuid(request.getUuid(), tag.getId());
        }
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean enabledBefore = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var page = requestService.findPage(new FuneralRequestFilterDto(), 0, 5, "createdAt,asc");
            assertEquals(5, page.content().size());
            assertEquals(12, page.totalElements());
            assertTrue(page.content().stream().allMatch(dto -> dto.getTags().size() == 1));
            // SELECT страницы с to-one связями, COUNT и один SELECT коллекций тегов.
            assertEquals(3, statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(enabledBefore);
        }
    }

    @Test
    void tagSchemaEnforcesUniqueNamesForeignKeysAndUniquePairs() throws Exception {
        Tag tag = context.getBean(TagService.class).create("VIP");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertEquals("23505", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO tags(name) VALUES ('vip')")).getSQLState());
            assertEquals("23502", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO tags(name) VALUES (NULL)")).getSQLState());
            assertEquals("23514", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO tags(name) VALUES ('  ')")).getSQLState());
            statement.execute("INSERT INTO funeral_request_tags VALUES (1, " + tag.getId() + ")");
            assertEquals("23505", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO funeral_request_tags VALUES (1, " + tag.getId() + ")")).getSQLState());
            assertEquals("23503", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO funeral_request_tags VALUES (1, 999999)")).getSQLState());
            assertEquals("23503", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO funeral_request_tags VALUES (999999, " + tag.getId() + ")")).getSQLState());
            assertEquals("23503", assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("DELETE FROM tags WHERE id = " + tag.getId())).getSQLState());
        }
    }

    @Test
    void customDisabledTypeCanBeReadThroughExistingRelationship() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO ceremony_types (id, code, name, enabled) VALUES (99, 'CUSTOM', 'Пользовательский тип', false)");
            statement.execute("UPDATE funeral_requests SET ceremony_type_id = 99 WHERE id = 1");
        }
        var read = requests.findById(1L).orElseThrow();
        assertEquals(99L, read.getCeremonyType().getId());
        assertEquals("CUSTOM", read.getCeremonyType().getCode());
        assertFalse(read.getCeremonyType().isEnabled());
    }

    private FuneralRequest newRequest() {
        FuneralRequest request = new FuneralRequest(null, clients.findById(1L).orElseThrow(), "Тестовый умерший",
                LocalDate.now().plusDays(5), new CeremonyType(1L, "BURIAL", "Захоронение", true), RequestStatus.NEW,
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
