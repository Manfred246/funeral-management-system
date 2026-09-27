package ru.funeralagency.repository.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.funeralagency.exception.DatabaseException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseFailureTest {
    @Test
    void clientRepositoryWrapsConnectionFailure() {
        // Несуществующий JDBC-драйвер даёт SQLException без обращения к сети.
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:missing:test");
        JdbcClientRepository repository = new JdbcClientRepository(source);
        DatabaseException error = assertThrows(DatabaseException.class, repository::findAll);
        assertInstanceOf(SQLException.class, error.getCause());
    }

    @Test
    void requestRepositoryWrapsConnectionFailure() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:missing:test");
        JdbcFuneralRequestRepository repository = new JdbcFuneralRequestRepository(source);
        DatabaseException error = assertThrows(DatabaseException.class, repository::findAll);
        assertInstanceOf(SQLException.class, error.getCause());
    }
}
