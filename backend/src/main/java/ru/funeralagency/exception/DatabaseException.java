package ru.funeralagency.exception;

import java.sql.SQLException;

public class DatabaseException extends RuntimeException {
    private final String sqlState;

    public DatabaseException(String message, SQLException cause) {
        super(message, cause);
        sqlState = cause.getSQLState();
    }

    public String getSqlState() {
        return sqlState;
    }
}
