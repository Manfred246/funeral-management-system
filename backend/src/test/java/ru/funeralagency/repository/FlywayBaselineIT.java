package ru.funeralagency.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.funeralagency.Main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlywayBaselineIT {
    @Test
    void v7PreservesCodesAlreadyGeneratedByV6() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        String schema = "code_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = new DriverManagerDataSource(url, username, password);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        try {
            String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
            DriverManagerDataSource source = new DriverManagerDataSource(testUrl, username, password);
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("6").load().migrate();
            String before;
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of(System.getProperty("project.root"), "database", "seed.sql")));
                try (ResultSet result = statement.executeQuery("SELECT string_agg(request_code, ',' ORDER BY id) FROM funeral_requests")) {
                    assertTrue(result.next());
                    before = result.getString(1);
                    assertNotNull(before);
                }
            }
            var migration = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
            assertEquals(1, migration.migrationsExecuted);
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT string_agg(request_code, ',' ORDER BY id) FROM funeral_requests")) {
                assertTrue(result.next());
                assertEquals(before, result.getString(1));
            }
        } finally {
            try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void existingKr1SchemaIsBaselinedWithoutChangingData() throws Exception {
        String url = requiredEnvironment("TEST_DB_URL");
        String username = requiredEnvironment("TEST_DB_USERNAME");
        String password = requiredEnvironment("TEST_DB_PASSWORD");
        String schema = "baseline_test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = new DriverManagerDataSource(url, username, password);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        try {
            String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
            DriverManagerDataSource source = new DriverManagerDataSource(testUrl, username, password);
            String before;
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("SELECT current_schema()")) {
                    assertTrue(result.next());
                    assertEquals(schema, result.getString(1));
                }
                for (String filename : new String[]{"schema.sql", "seed.sql"}) {
                    statement.execute(Files.readString(Path.of(System.getProperty("project.root"), "database", filename)));
                }
                // Историческая заявка проверяет, что V2 считает priority от created_at, а не CURRENT_DATE.
                statement.execute("""
                        UPDATE funeral_requests
                        SET created_at = TIMESTAMP '2000-01-01 12:00:00', ceremony_date = DATE '2000-01-04'
                        WHERE id = 1
                        """);
                statement.execute("INSERT INTO ceremony_types(id, name) VALUES (99, 'Старый пользовательский тип')");
                before = snapshot(statement);
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
            try (ConfigurableApplicationContext context = application.run()) {
                assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
                Flyway flyway = context.getBean(Flyway.class);
                var applied = flyway.info().applied();
                assertEquals(7, applied.length);
                assertEquals("1", applied[0].getVersion().getVersion());
                assertEquals("BASELINE", applied[0].getType().name());
                assertEquals("2", applied[1].getVersion().getVersion());
                assertEquals("SQL", applied[1].getType().name());
                assertEquals("3", applied[2].getVersion().getVersion());
                assertEquals("SQL", applied[2].getType().name());
                assertEquals("4", applied[3].getVersion().getVersion());
                assertEquals("SQL", applied[3].getType().name());
                assertEquals("5", applied[4].getVersion().getVersion());
                assertEquals("6", applied[5].getVersion().getVersion());
                assertEquals("7", applied[6].getVersion().getVersion());
                assertEquals(0, context.getBean(RequestNoteRepository.class).count());
                assertEquals(0, context.getBean(TagRepository.class).count());
                assertEquals(0, flyway.migrate().migrationsExecuted);
                assertEquals(6, context.getBean(ClientRepository.class).count());
                assertEquals(12, context.getBean(FuneralRequestRepository.class).count());
                try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                    assertEquals(before, snapshot(statement));
                    var types = context.getBean(CeremonyTypeRepository.class);
                    assertEquals("BURIAL", types.findById(1L).orElseThrow().getCode());
                    assertEquals("Захоронение", types.findById(1L).orElseThrow().getName());
                    assertEquals("CREMATION", types.findById(2L).orElseThrow().getCode());
                    assertTrue(types.findById(2L).orElseThrow().isEnabled());
                    assertEquals("LEGACY_99", types.findById(99L).orElseThrow().getCode());
                    try (ResultSet result = statement.executeQuery("""
                            SELECT id, uuid, request_code, urgent, archived, priority,
                                   created_at, updated_at, version
                            FROM funeral_requests ORDER BY id
                            """)) {
                        int rows = 0;
                        while (result.next()) {
                            rows++;
                            assertNotNull(result.getObject("uuid", UUID.class));
                            var createdAt = result.getTimestamp("created_at").toLocalDateTime();
                            assertEquals(String.format(java.util.Locale.ROOT, "FR-%d-%06d",
                                    createdAt.getYear(), result.getLong("id")), result.getString("request_code"));
                            assertFalse(result.getBoolean("urgent"));
                            assertFalse(result.getBoolean("archived"));
                            assertTrue(result.getInt("priority") >= 0 && result.getInt("priority") <= 2);
                            if (result.getLong("id") == 1L) {
                                assertEquals(2, result.getInt("priority"));
                            }
                            assertEquals(createdAt, result.getTimestamp("updated_at").toLocalDateTime());
                            assertEquals(0L, result.getLong("version"));
                        }
                        assertEquals(12, rows);
                    }
                }
            }
        } finally {
            if (schema.matches("baseline_test_[a-f0-9]{32}")) {
                try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }

    private String snapshot(Statement statement) throws Exception {
        StringBuilder snapshot = new StringBuilder();
        for (String table : new String[]{"clients", "funeral_requests", "ceremony_types", "request_statuses"}) {
            String columns = switch (table) {
                case "clients" -> "id, full_name, phone, email, created_at";
                case "funeral_requests" -> "id, client_id, deceased_full_name, ceremony_date, "
                        + "ceremony_type_id, status_id, price, comment, created_at";
                default -> "id, name";
            };
            try (ResultSet result = statement.executeQuery("SELECT row_to_json(t)::text FROM (SELECT "
                    + columns + " FROM " + table + " ORDER BY id) t")) {
                while (result.next()) {
                    snapshot.append(table).append(':').append(result.getString(1)).append('\n');
                }
            }
        }
        return snapshot.toString();
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Для PostgreSQL-тестов задайте " + name);
        }
        return value;
    }
}
