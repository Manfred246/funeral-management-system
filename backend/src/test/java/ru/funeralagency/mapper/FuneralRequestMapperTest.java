package ru.funeralagency.mapper;

import org.junit.jupiter.api.Test;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

        FuneralRequest request = mapper.toEntity(dto);

        assertEquals(4L, request.getClientId());
        assertEquals("Иванов Иван Иванович", request.getDeceasedFullName());
        assertNull(request.getId());
        assertNull(request.getStatus());
        assertNull(request.getCreatedAt());
        assertNull(request.getComment());
    }

    @Test
    void mapsEntityToResponseDto() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 22, 10, 0);
        FuneralRequest request = new FuneralRequest(
                8L,
                4L,
                "Иванов Иван Иванович",
                LocalDate.of(2027, 2, 1),
                CeremonyType.CREMATION,
                RequestStatus.NEW,
                new BigDecimal("12000.00"),
                createdAt,
                null
        );

        var response = mapper.toResponseDto(request);

        assertEquals(8L, response.getId());
        assertEquals(RequestStatus.NEW, response.getStatus());
        assertEquals(createdAt, response.getCreatedAt());
    }
}
