package ru.funeralagency.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.funeralagency.exception.InvalidFuneralRequestException;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuneralRequestValidationRulesTest {

    @Test
    void acceptsValidRequest() {
        assertDoesNotThrow(() -> FuneralRequestValidationRules.validate(validRequest()));
    }

    @Test
    void rejectsBlankDeceasedName() {
        FuneralRequest request = validRequest();
        request.setDeceasedFullName("   ");

        assertThrows(
                InvalidFuneralRequestException.class,
                () -> FuneralRequestValidationRules.validate(request)
        );
    }

    @Test
    void rejectsNegativePrice() {
        FuneralRequest request = validRequest();
        request.setPrice(new BigDecimal("-0.01"));

        assertThrows(
                InvalidFuneralRequestException.class,
                () -> FuneralRequestValidationRules.validate(request)
        );
    }

    @Test
    void rejectsPastCeremonyDate() {
        FuneralRequest request = validRequest();
        request.setCeremonyDate(LocalDate.now().minusDays(1));

        assertThrows(
                InvalidFuneralRequestException.class,
                () -> FuneralRequestValidationRules.validate(request)
        );
    }

    @Test
    void allowsStatusUpdateAfterCeremonyWhenDateIsUnchanged() {
        FuneralRequest request = validRequest();
        LocalDate pastDate = LocalDate.now().minusDays(1);
        request.setCeremonyDate(pastDate);
        request.setStatus(RequestStatus.CONFIRMED);

        assertDoesNotThrow(
                () -> FuneralRequestValidationRules.validateForUpdate(request, pastDate)
        );
    }

    @Test
    void rejectsForbiddenStatusTransitions() {
        assertThrows(
                BusinessException.class,
                () -> FuneralRequestValidationRules.validateStatusTransition(
                        RequestStatus.NEW,
                        RequestStatus.COMPLETED
                )
        );
        assertThrows(
                BusinessException.class,
                () -> FuneralRequestValidationRules.validateStatusTransition(
                        RequestStatus.COMPLETED,
                        RequestStatus.CANCELLED
                )
        );
        assertThrows(
                BusinessException.class,
                () -> FuneralRequestValidationRules.validateStatusTransition(
                        RequestStatus.CANCELLED,
                        RequestStatus.IN_PROGRESS
                )
        );
    }

    @Test
    void normalizesNameAndEmptyComment() {
        FuneralRequest request = validRequest();
        request.setDeceasedFullName("  Иванов   Иван ");
        request.setComment("  ");

        FuneralRequestValidationRules.normalize(request);

        assertEquals("Иванов Иван", request.getDeceasedFullName());
        assertEquals(null, request.getComment());
    }

    private FuneralRequest validRequest() {
        return new FuneralRequest(
                1L,
                1L,
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(1),
                CeremonyType.BURIAL,
                RequestStatus.NEW,
                new BigDecimal("10000.00"),
                LocalDateTime.now(),
                "Комментарий"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.123", "0.001", "10000000000.00"})
    void rejectsPricesThatCannotBeStoredExactly(String price) {
        FuneralRequest request = validRequest();
        request.setPrice(new BigDecimal(price));
        assertThrows(InvalidFuneralRequestException.class, () -> FuneralRequestValidationRules.validate(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100.1200", "9999999999.99"})
    void acceptsPricesWithinDatabaseRange(String price) {
        FuneralRequest request = validRequest();
        request.setPrice(new BigDecimal(price));
        assertDoesNotThrow(() -> FuneralRequestValidationRules.validate(request));
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(InvalidFuneralRequestException.class, () -> FuneralRequestValidationRules.validate(null));
        FuneralRequest request = validRequest();
        request.setClientId(0L);
        assertThrows(InvalidFuneralRequestException.class, () -> FuneralRequestValidationRules.validate(request));
        request.setClientId(1L);
        request.setCeremonyType(null);
        assertThrows(InvalidFuneralRequestException.class, () -> FuneralRequestValidationRules.validate(request));
        request.setCeremonyType(CeremonyType.BURIAL);
        request.setPrice(null);
        assertThrows(InvalidFuneralRequestException.class, () -> FuneralRequestValidationRules.validate(request));
    }

    @Test
    void rejectsChangingCeremonyDateToPast() {
        FuneralRequest request = validRequest();
        LocalDate previousDate = request.getCeremonyDate();
        request.setCeremonyDate(LocalDate.now().minusDays(1));
        assertThrows(InvalidFuneralRequestException.class,
                () -> FuneralRequestValidationRules.validateForUpdate(request, previousDate));
    }
}
