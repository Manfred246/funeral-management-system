package ru.funeralagency.repository.jdbc;

import org.springframework.stereotype.Repository;
import ru.funeralagency.exception.DatabaseException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.ClientRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcClientRepository implements ClientRepository {
    private final DataSource dataSource;

    public JdbcClientRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Client create(Client client) {
        String sql = """
                INSERT INTO clients (full_name, phone, email) VALUES (?, ?, ?)
                RETURNING id, full_name, phone, email
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, client.getFullName());
            statement.setString(2, client.getPhone());
            statement.setString(3, client.getEmail());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("INSERT did not return a client");
                }
                return mapRow(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при создании клиента", e);
        }
    }

    @Override
    public List<Client> findAll() {
        String sql = "SELECT id, full_name, phone, email FROM clients ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<Client> clients = new ArrayList<>();
            while (result.next()) {
                clients.add(mapRow(result));
            }
            return clients;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении клиентов", e);
        }
    }

    @Override
    public Optional<Client> findById(long id) {
        String sql = "SELECT id, full_name, phone, email FROM clients WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapRow(result));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении клиента", e);
        }
    }

    @Override
    public Client update(Client client) {
        String sql = """
                UPDATE clients SET full_name = ?, phone = ?, email = ? WHERE id = ?
                RETURNING id, full_name, phone, email
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, client.getFullName());
            statement.setString(2, client.getPhone());
            statement.setString(3, client.getEmail());
            statement.setLong(4, client.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new EntityNotFoundException("Клиент с ID " + client.getId() + " не найден");
                }
                return mapRow(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при изменении клиента", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM clients WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при удалении клиента", e);
        }
    }

    private Client mapRow(ResultSet result) throws SQLException {
        return new Client(
                result.getLong("id"),
                result.getString("full_name"),
                result.getString("phone"),
                result.getString("email")
        );
    }
}
