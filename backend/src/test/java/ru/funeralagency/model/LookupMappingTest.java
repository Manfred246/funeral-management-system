package ru.funeralagency.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LookupMappingTest {
    @Test
    void convertersPreserveDatabaseIdsAndNulls() {
        CeremonyTypeConverter types = new CeremonyTypeConverter();
        RequestStatusConverter statuses = new RequestStatusConverter();
        for (CeremonyType type : CeremonyType.values()) {
            assertEquals(type.getDatabaseId(), types.convertToDatabaseColumn(type));
            assertEquals(type, types.convertToEntityAttribute(type.getDatabaseId()));
        }
        for (RequestStatus status : RequestStatus.values()) {
            assertEquals(status.getDatabaseId(), statuses.convertToDatabaseColumn(status));
            assertEquals(status, statuses.convertToEntityAttribute(status.getDatabaseId()));
        }
        org.junit.jupiter.api.Assertions.assertNull(types.convertToDatabaseColumn(null));
        org.junit.jupiter.api.Assertions.assertNull(types.convertToEntityAttribute(null));
        org.junit.jupiter.api.Assertions.assertNull(statuses.convertToDatabaseColumn(null));
        org.junit.jupiter.api.Assertions.assertNull(statuses.convertToEntityAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> types.convertToEntityAttribute(99));
        assertThrows(IllegalArgumentException.class, () -> statuses.convertToEntityAttribute(99));
    }

    @Test
    void ceremonyIdsMatchSchema() {
        assertEquals(1, CeremonyType.BURIAL.getDatabaseId());
        assertEquals(2, CeremonyType.CREMATION.getDatabaseId());
        assertEquals(CeremonyType.BURIAL, CeremonyType.fromDatabaseId(1));
        assertEquals(CeremonyType.CREMATION, CeremonyType.fromDatabaseId(2));
    }

    @Test
    void statusIdsMatchSchema() {
        assertEquals(1, RequestStatus.NEW.getDatabaseId());
        assertEquals(2, RequestStatus.CONFIRMED.getDatabaseId());
        assertEquals(3, RequestStatus.IN_PROGRESS.getDatabaseId());
        assertEquals(4, RequestStatus.COMPLETED.getDatabaseId());
        assertEquals(5, RequestStatus.CANCELLED.getDatabaseId());
        assertEquals(RequestStatus.NEW, RequestStatus.fromDatabaseId(1));
        assertEquals(RequestStatus.CANCELLED, RequestStatus.fromDatabaseId(5));
    }

    @Test
    void unknownIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CeremonyType.fromDatabaseId(0));
        assertThrows(IllegalArgumentException.class, () -> RequestStatus.fromDatabaseId(99));
    }
}
