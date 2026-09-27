package ru.funeralagency.repository.jdbc;

import org.springframework.stereotype.Repository;
import ru.funeralagency.exception.DatabaseException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.FuneralRequestRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFuneralRequestRepository implements FuneralRequestRepository {
    private static final String COLUMNS = """
            id, client_id, deceased_full_name, ceremony_date, ceremony_type_id,
            status_id, price, comment, created_at
            """;
    private static final String SELECT_SQL = "SELECT " + COLUMNS + " FROM funeral_requests";

    private final DataSource dataSource;

    public JdbcFuneralRequestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public FuneralRequest create(FuneralRequest request) {
        String sql = """
                INSERT INTO funeral_requests
                    (client_id, deceased_full_name, ceremony_date, ceremony_type_id, status_id, price, comment)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING
                """ + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            RequestStatus status = request.getStatus();
            if (status == null) {
                status = RequestStatus.NEW;
            }
            bindRequest(statement, request, status);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("INSERT did not return a request");
                }
                return mapRow(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при создании заявки", e);
        }
    }

    @Override
    public List<FuneralRequest> findAll() {
        return findAllSorted("id ASC");
    }

    @Override
    public Optional<FuneralRequest> findById(long id) {
        String sql = SELECT_SQL + " WHERE id = ?";
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
            throw new DatabaseException("Ошибка при получении заявки", e);
        }
    }

    @Override
    public FuneralRequest update(FuneralRequest request) {
        String sql = """
                UPDATE funeral_requests
                SET client_id = ?, deceased_full_name = ?, ceremony_date = ?,
                    ceremony_type_id = ?, status_id = ?, price = ?, comment = ?
                WHERE id = ? RETURNING
                """ + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRequest(statement, request, request.getStatus());
            statement.setLong(8, request.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new EntityNotFoundException("Заявка с ID " + request.getId() + " не найдена");
                }
                return mapRow(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при изменении заявки", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM funeral_requests WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при удалении заявки", e);
        }
    }

    @Override
    public boolean existsByClientId(long clientId) {
        String sql = "SELECT 1 FROM funeral_requests WHERE client_id = ? LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, clientId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при проверке заявок клиента", e);
        }
    }

    @Override
    public List<FuneralRequest> findByDeceasedName(String name) {
        String sql = SELECT_SQL + " WHERE LOWER(deceased_full_name) LIKE LOWER(?) ESCAPE '!' ORDER BY id";
        // Символы %, _ и ! из пользовательского ввода ищем буквально.
        String escapedName = name.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + escapedName + "%");
            try (ResultSet result = statement.executeQuery()) {
                return readList(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при поиске заявок по ФИО", e);
        }
    }

    @Override
    public List<FuneralRequest> findByClientId(long clientId) {
        String sql = SELECT_SQL + " WHERE client_id = ? ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, clientId);
            try (ResultSet result = statement.executeQuery()) {
                return readList(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при поиске заявок клиента", e);
        }
    }

    @Override
    public List<FuneralRequest> findByStatus(RequestStatus status) {
        String sql = SELECT_SQL + " WHERE status_id = ? ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, status.getDatabaseId());
            try (ResultSet result = statement.executeQuery()) {
                return readList(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при фильтрации заявок по статусу", e);
        }
    }

    @Override
    public List<FuneralRequest> findByCeremonyType(CeremonyType type) {
        String sql = SELECT_SQL + " WHERE ceremony_type_id = ? ORDER BY id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, type.getDatabaseId());
            try (ResultSet result = statement.executeQuery()) {
                return readList(result);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при фильтрации заявок по типу церемонии", e);
        }
    }

    @Override
    public List<FuneralRequest> findAllOrderByCeremonyDate(boolean ascending) {
        if (ascending) {
            return findAllSorted("ceremony_date ASC, id ASC");
        }
        return findAllSorted("ceremony_date DESC, id ASC");
    }

    @Override
    public List<FuneralRequest> findAllOrderByPrice(boolean ascending) {
        if (ascending) {
            return findAllSorted("price ASC, id ASC");
        }
        return findAllSorted("price DESC, id ASC");
    }

    // В этот private-метод передаются только фиксированные строки из методов выше.
    private List<FuneralRequest> findAllSorted(String orderBy) {
        String sql = SELECT_SQL + " ORDER BY " + orderBy;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return readList(result);
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка при получении списка заявок", e);
        }
    }

    private void bindRequest(PreparedStatement statement, FuneralRequest request, RequestStatus status)
            throws SQLException {
        statement.setObject(1, request.getClientId(), Types.BIGINT);
        statement.setString(2, request.getDeceasedFullName());
        statement.setObject(3, request.getCeremonyDate(), Types.DATE);
        if (request.getCeremonyType() == null) {
            statement.setNull(4, Types.INTEGER);
        } else {
            statement.setInt(4, request.getCeremonyType().getDatabaseId());
        }
        if (status == null) {
            statement.setNull(5, Types.INTEGER);
        } else {
            statement.setInt(5, status.getDatabaseId());
        }
        statement.setBigDecimal(6, request.getPrice());
        statement.setString(7, request.getComment());
    }

    private List<FuneralRequest> readList(ResultSet result) throws SQLException {
        List<FuneralRequest> requests = new ArrayList<>();
        while (result.next()) {
            requests.add(mapRow(result));
        }
        return requests;
    }

    private FuneralRequest mapRow(ResultSet result) throws SQLException {
        try {
            return new FuneralRequest(
                    result.getLong("id"),
                    result.getLong("client_id"),
                    result.getString("deceased_full_name"),
                    result.getObject("ceremony_date", LocalDate.class),
                    CeremonyType.fromDatabaseId(result.getInt("ceremony_type_id")),
                    RequestStatus.fromDatabaseId(result.getInt("status_id")),
                    result.getBigDecimal("price"),
                    result.getObject("created_at", LocalDateTime.class),
                    result.getString("comment")
            );
        } catch (IllegalArgumentException e) {
            throw new SQLDataException("В справочнике найден неизвестный для Java enum ID", "22000", e);
        }
    }
}
