package ru.funeralagency.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import ru.funeralagency.mapper.FuneralRequestMapper;

import static org.junit.jupiter.api.Assertions.*;

class FuneralRequestDtoTest {
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final FuneralRequestMapper mapper = new FuneralRequestMapper();

    @Test
    void oldCreateBodyDefaultsUrgentToFalseAndCannotSupplyServerFields() throws Exception {
        FuneralRequestCreateDto dto = objectMapper.readValue("""
                {
                  "clientId": 1,
                  "deceasedFullName": "Иван Иванов",
                  "ceremonyDate": "2027-01-01",
                  "ceremonyTypeId": 1,
                  "price": 100,
                  "id": 999,
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "requestCode": "supplied",
                  "priority": 999,
                  "archived": true,
                  "createdAt": "2000-01-01T00:00:00",
                  "updatedAt": "2000-01-01T00:00:00",
                  "version": 999
                }
                """, FuneralRequestCreateDto.class);
        var request = mapper.toEntity(dto);
        assertEquals(1L, dto.getClientId());
        assertFalse(request.isUrgent());
        assertFalse(request.isArchived());
        assertNull(request.getId());
        assertNull(request.getUuid());
        assertNull(request.getRequestCode());
        assertNull(request.getPriority());
        assertNull(request.getCreatedAt());
        assertNull(request.getUpdatedAt());
        assertNull(request.getVersion());
    }

    @Test
    void updateMapsUrgentButCannotSupplyImmutableFields() throws Exception {
        FuneralRequestUpdateDto dto = objectMapper.readValue("""
                {
                  "clientId": 1,
                  "urgent": true,
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "requestCode": "changed",
                  "createdAt": "2000-01-01T00:00:00",
                  "updatedAt": "2000-01-01T00:00:00",
                  "priority": 999,
                  "archived": true,
                  "version": 999
                }
                """, FuneralRequestUpdateDto.class);
        var request = mapper.toEntity(dto);
        assertTrue(request.isUrgent());
        assertFalse(request.isArchived());
        assertNull(request.getUuid());
        assertNull(request.getRequestCode());
        assertNull(request.getCreatedAt());
        assertNull(request.getUpdatedAt());
        assertNull(request.getPriority());
        assertNull(request.getVersion());
        assertEquals(999L, dto.getVersion());
    }
}
