package ru.funeralagency.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** JDBC-реализация хранилища заявок. */
@Repository
public class JdbcFuneralRequestRepository implements FuneralRequestRepository {

    private static final String COLUMNS = """
            id, client_id, deceased_full_name, ceremony_date, ceremony_type,
            status, price, created_at, comment
            """;

    private static final String INSERT_SQL = """
            INSERT INTO funeral_requests (
                client_id, deceased_full_name, ceremony_date, ceremony_type,
                status, price, created_at, comment
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM funeral_requests WHERE id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT " + COLUMNS + " FROM funeral_requests ORDER BY id";

    private static final String UPDATE_SQL = """
            UPDATE funeral_requests
            SET client_id = ?, deceased_full_name = ?, ceremony_date = ?, ceremony_type = ?,
                status = ?, price = ?, comment = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM funeral_requests WHERE id = ?";

    private static final RowMapper<FuneralRequest> ROW_MAPPER = (resultSet, rowNumber) ->
            new FuneralRequest(
                    resultSet.getLong("id"),
                    resultSet.getLong("client_id"),
                    resultSet.getString("deceased_full_name"),
                    resultSet.getDate("ceremony_date").toLocalDate(),
                    CeremonyType.valueOf(resultSet.getString("ceremony_type")),
                    RequestStatus.valueOf(resultSet.getString("status")),
                    resultSet.getBigDecimal("price"),
                    resultSet.getTimestamp("created_at").toLocalDateTime(),
                    resultSet.getString("comment")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcFuneralRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FuneralRequest save(FuneralRequest request) {
        requireRequest(request, false);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, request.getClientId());
            statement.setString(2, request.getDeceasedFullName());
            statement.setObject(3, request.getCeremonyDate());
            statement.setString(4, request.getCeremonyType().name());
            statement.setString(5, request.getStatus().name());
            statement.setBigDecimal(6, request.getPrice());
            statement.setTimestamp(7, Timestamp.valueOf(request.getCreatedAt()));
            statement.setString(8, request.getComment());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            request.setId(generatedId.longValue());
        }
        return request;
    }

    @Override
    public Optional<FuneralRequest> findById(Long id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<FuneralRequest> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, ROW_MAPPER);
    }

    @Override
    public void update(FuneralRequest request) {
        requireRequest(request, true);
        jdbcTemplate.update(
                UPDATE_SQL,
                request.getClientId(),
                request.getDeceasedFullName(),
                request.getCeremonyDate(),
                request.getCeremonyType().name(),
                request.getStatus().name(),
                request.getPrice(),
                request.getComment(),
                request.getId()
        );
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update(DELETE_SQL, id);
    }

    private void requireRequest(FuneralRequest request, boolean idRequired) {
        if (request == null || (idRequired && request.getId() == null)) {
            throw new IllegalArgumentException("Для операции нужна корректная заявка");
        }
    }
}
