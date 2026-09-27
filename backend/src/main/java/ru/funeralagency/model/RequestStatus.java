package ru.funeralagency.model;

public enum RequestStatus {
    NEW(1),
    CONFIRMED(2),
    IN_PROGRESS(3),
    COMPLETED(4),
    CANCELLED(5);

    private final int databaseId;

    RequestStatus(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public static RequestStatus fromDatabaseId(int id) {
        for (RequestStatus status : values()) {
            if (status.databaseId == id) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный ID статуса: " + id);
    }
}
