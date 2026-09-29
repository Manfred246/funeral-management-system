package ru.funeralagency.validation;

import org.junit.jupiter.api.Test;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.exception.InvalidFuneralRequestException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuneralRequestDtoValidatorTest {

    private final FuneralRequestDtoValidator validator = new FuneralRequestDtoValidator();

    @Test
    void acceptsValidCreateDto() {
        FuneralRequestCreateDto dto = new FuneralRequestCreateDto(
                1L,
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(1),
                CeremonyType.BURIAL,
                new BigDecimal("10000.00"),
                null
        );

        assertDoesNotThrow(() -> validator.validateForCreate(dto));
    }

    @Test
    void rejectsPastDateWhenCreating() {
        FuneralRequestCreateDto dto = new FuneralRequestCreateDto(
                1L,
                "Иванов Иван Иванович",
                LocalDate.now().minusDays(1),
                CeremonyType.BURIAL,
                new BigDecimal("10000.00"),
                null
        );

        assertThrows(
                InvalidFuneralRequestException.class,
                () -> validator.validateForCreate(dto)
        );
    }

    @Test
    void rejectsUpdateWithoutStatus() {
        FuneralRequestUpdateDto dto = new FuneralRequestUpdateDto(
                1L,
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(1),
                CeremonyType.CREMATION,
                null,
                BigDecimal.ZERO,
                null
        );

        assertThrows(
                InvalidFuneralRequestException.class,
                () -> validator.validateForUpdate(dto)
        );
    }

    @Test
    void acceptsValidUpdateDto() {
        FuneralRequestUpdateDto dto = new FuneralRequestUpdateDto(
                1L,
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(1),
                CeremonyType.CREMATION,
                RequestStatus.CONFIRMED,
                BigDecimal.ZERO,
                null
        );

        assertDoesNotThrow(() -> validator.validateForUpdate(dto));
    }
}
