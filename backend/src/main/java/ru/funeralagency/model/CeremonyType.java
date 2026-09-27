package ru.funeralagency.model;

public enum CeremonyType {
    BURIAL(1),
    CREMATION(2);

    private final int databaseId;

    CeremonyType(int databaseId) {
        this.databaseId = databaseId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public static CeremonyType fromDatabaseId(int id) {
        for (CeremonyType type : values()) {
            if (type.databaseId == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный ID типа церемонии: " + id);
    }
}
