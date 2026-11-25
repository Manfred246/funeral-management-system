package ru.funeralagency.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuneralRequestMappingTest {
    @Test
    void requestCodeIsReadOnlyAndRetrievedAfterInsert() throws Exception {
        var field = FuneralRequest.class.getDeclaredField("requestCode");
        Column column = field.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("request_code", column.name());
        assertFalse(column.insertable());
        assertFalse(column.updatable());
        assertFalse(column.nullable());
        assertTrue(column.unique());

        Generated generated = field.getAnnotation(Generated.class);
        assertNotNull(generated);
        assertArrayEquals(new EventType[]{EventType.INSERT}, generated.event());
        assertFalse(generated.writable());
    }

    @Test
    void requestCodeInputsHaveIdentityIdAndNonUpdatableCreationTime() throws Exception {
        var id = FuneralRequest.class.getDeclaredField("id");
        assertEquals(Long.class, id.getType());
        assertNotNull(id.getAnnotation(Id.class));
        GeneratedValue generatedId = id.getAnnotation(GeneratedValue.class);
        assertNotNull(generatedId);
        assertEquals(GenerationType.IDENTITY, generatedId.strategy());

        Column createdAt = FuneralRequest.class.getDeclaredField("createdAt").getAnnotation(Column.class);
        assertNotNull(createdAt);
        assertEquals("created_at", createdAt.name());
        assertFalse(createdAt.updatable());
    }

    @Test
    void versionIsManagedByJpa() throws Exception {
        var field = FuneralRequest.class.getDeclaredField("version");
        assertEquals(Long.class, field.getType());
        assertNotNull(field.getAnnotation(Version.class));
    }
}
