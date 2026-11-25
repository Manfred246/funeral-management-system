package ru.funeralagency.mapper;

import org.junit.jupiter.api.Test;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FuneralRequestMapperTest {

    private final FuneralRequestMapper mapper = new FuneralRequestMapper();

    @Test
    void mapsCreateDtoWithoutServerGeneratedFields() {
        FuneralRequestCreateDto dto = new FuneralRequestCreateDto(
                4L,
                "  Иванов   Иван Иванович ",
                LocalDate.of(2027, 2, 1),
                CeremonyType.BURIAL,
                new BigDecimal("12000.00"),
                "  "
        );
        dto.setUrgent(true);

        FuneralRequest request = mapper.toEntity(dto);

        assertNull(request.getClient());
        assertEquals(dto.getDeceasedFullName(), request.getDeceasedFullName());
        assertNull(request.getId());
        assertNull(request.getStatus());
        assertNull(request.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertTrue(request.isUrgent());
        assertNull(request.getUuid());
        assertNull(request.getRequestCode());
        assertNull(request.getPriority());
        assertNull(request.getUpdatedAt());
        assertNull(request.getVersion());
        assertEquals(dto.getComment(), request.getComment());
    }

    @Test
    void mapsEntityToResponseDto() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 22, 10, 0);
        FuneralRequest request = new FuneralRequest(
                8L,
                new Client(4L, "Клиент", "+79991234567", null),
                "Иванов Иван Иванович",
                LocalDate.of(2027, 2, 1),
                CeremonyType.CREMATION,
                RequestStatus.NEW,
                new BigDecimal("12000.00"),
                createdAt,
                null
        );
        UUID uuid = UUID.randomUUID();
        request.setUuid(uuid);
        request.setRequestCode("FR-2026-000008");
        request.setUrgent(true);
        request.setPriority(2);
        request.setArchived(true);
        request.setUpdatedAt(createdAt.plusDays(1));
        request.setVersion(3L);

        var response = mapper.toResponseDto(request);

        assertEquals(8L, response.getId());
        assertEquals(4L, response.getClientId());
        assertEquals(RequestStatus.NEW, response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
        assertEquals(uuid, response.getUuid());
        assertEquals("FR-2026-000008", response.getRequestCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.isUrgent());
        org.junit.jupiter.api.Assertions.assertTrue(response.isArchived());
        assertEquals(2, response.getPriority());
        assertEquals(createdAt.plusDays(1), response.getUpdatedAt());
        assertEquals(3L, response.getVersion());
    }
}
