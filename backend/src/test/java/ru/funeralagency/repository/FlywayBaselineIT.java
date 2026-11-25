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
                before = snapshot(statement);
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
            try (ConfigurableApplicationContext context = application.run()) {
                assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
                Flyway flyway = context.getBean(Flyway.class);
                assertEquals("1", flyway.info().current().getVersion().getVersion());
                assertEquals("BASELINE", flyway.info().current().getType().name());
                assertEquals(0, flyway.migrate().migrationsExecuted);
                assertEquals(6, context.getBean(ClientRepository.class).count());
                assertEquals(12, context.getBean(FuneralRequestRepository.class).count());
                try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                    assertEquals(before, snapshot(statement));
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
            try (ResultSet result = statement.executeQuery("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY id")) {
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
