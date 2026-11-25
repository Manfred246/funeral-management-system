package ru.funeralagency.model;

import jakarta.persistence.Convert;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.java.LongJavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LookupMappingTest {
    @Test
    void requestStatusConverterPreservesDatabaseIdsAndNulls() {
        RequestStatusConverter statuses = new RequestStatusConverter();
        for (RequestStatus status : RequestStatus.values()) {
            assertEquals(status.getDatabaseId(), statuses.convertToDatabaseColumn(status));
            assertEquals(status, statuses.convertToEntityAttribute(status.getDatabaseId()));
        }
        assertNull(statuses.convertToDatabaseColumn(null));
        assertNull(statuses.convertToEntityAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> statuses.convertToEntityAttribute(99));
    }

    @Test
    void ceremonyTypeUsesLongIdWithExistingIntegerJdbcMapping() throws Exception {
        assertFalse(CeremonyType.class.isEnum());
        var id = CeremonyType.class.getDeclaredField("id");
        assertEquals(Long.class, id.getType());
        assertEquals(SqlTypes.INTEGER, id.getAnnotation(JdbcTypeCode.class).value());
        assertEquals(1L, LongJavaType.INSTANCE.wrap(1, null));
        assertEquals(2, LongJavaType.INSTANCE.unwrap(2L, Integer.class, null));
        var relation = FuneralRequest.class.getDeclaredField("ceremonyType");
        assertNotNull(relation.getAnnotation(ManyToOne.class));
        assertEquals("ceremony_type_id", relation.getAnnotation(JoinColumn.class).name());
        assertNull(relation.getAnnotation(Convert.class));
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
    void unknownStatusIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RequestStatus.fromDatabaseId(99));
    }
}
