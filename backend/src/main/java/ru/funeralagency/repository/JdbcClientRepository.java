package ru.funeralagency.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.funeralagency.model.Client;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** JDBC-реализация репозитория клиентов. */
@Repository
public class JdbcClientRepository implements ClientRepository {

    private static final String INSERT_SQL = """
            INSERT INTO clients (full_name, phone, email)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, full_name, phone, email
            FROM clients
            WHERE id = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT id, full_name, phone, email
            FROM clients
            ORDER BY id
            """;

    private static final String UPDATE_SQL = """
            UPDATE clients
            SET full_name = ?, phone = ?, email = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM clients WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Client save(Client client) {
        if (client == null) {
            throw new IllegalArgumentException("Клиент не может быть null");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, client.getFullName());
            statement.setString(2, client.getPhone());
            statement.setString(3, client.getEmail());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            client.setId(generatedId.longValue());
        }
        return client;
    }

    @Override
    public Optional<Client> findById(Long id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, CLIENT_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    @Override
    public List<Client> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, CLIENT_ROW_MAPPER);
    }

    @Override
    public void update(Client client) {
        if (client == null || client.getId() == null) {
            throw new IllegalArgumentException("Для изменения нужен клиент с ID");
        }

        jdbcTemplate.update(
                UPDATE_SQL,
                client.getFullName(),
                client.getPhone(),
                client.getEmail(),
                client.getId()
        );
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update(DELETE_SQL, id);
    }

    private static final org.springframework.jdbc.core.RowMapper<Client> CLIENT_ROW_MAPPER =
            (resultSet, rowNumber) -> new Client(
                    resultSet.getLong("id"),
                    resultSet.getString("full_name"),
                    resultSet.getString("phone"),
                    resultSet.getString("email")
            );
}
