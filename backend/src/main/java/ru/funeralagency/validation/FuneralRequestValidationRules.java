package ru.funeralagency.validation;

import org.springframework.util.StringUtils;
import ru.funeralagency.exception.InvalidFuneralRequestException;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Проверка и нормализация заявки независимо от REST-слоя. */
public final class FuneralRequestValidationRules {

    public static final int MAX_DECEASED_FULL_NAME_LENGTH = 150;
    public static final int MAX_COMMENT_LENGTH = 1000;

    private FuneralRequestValidationRules() {
    }

    public static void normalize(FuneralRequest request) {
        if (request == null) {
            return;
        }
        request.setDeceasedFullName(normalizeSpaces(request.getDeceasedFullName()));
        request.setComment(normalizeNullable(request.getComment()));
    }

    public static void validate(FuneralRequest request) {
        validateCommon(request);
        rejectPastDate(request.getCeremonyDate());
    }

    /** Позволяет менять статус после церемонии, но запрещает назначать новую дату в прошлом. */
    public static void validateForUpdate(FuneralRequest request, LocalDate previousDate) {
        validateCommon(request);
        if (!request.getCeremonyDate().equals(previousDate)) {
            rejectPastDate(request.getCeremonyDate());
        }
    }

    private static void validateCommon(FuneralRequest request) {
        if (request == null) {
            throw new InvalidFuneralRequestException("Заявка не может быть null");
        }
        if (request.getClientId() == null || request.getClientId() <= 0) {
            throw new InvalidFuneralRequestException("ID клиента должен быть положительным числом");
        }
        if (!StringUtils.hasText(request.getDeceasedFullName())) {
            throw new InvalidFuneralRequestException("ФИО умершего обязательно");
        }
        if (request.getDeceasedFullName().length() > MAX_DECEASED_FULL_NAME_LENGTH) {
            throw new InvalidFuneralRequestException(
                    "ФИО умершего не должно превышать "
                            + MAX_DECEASED_FULL_NAME_LENGTH
                            + " символов"
            );
        }
        if (request.getCeremonyDate() == null) {
            throw new InvalidFuneralRequestException("Дата церемонии обязательна");
        }
        if (request.getCeremonyType() == null) {
            throw new InvalidFuneralRequestException("Тип церемонии обязателен");
        }
        if (request.getStatus() == null) {
            throw new InvalidFuneralRequestException("Статус заявки обязателен");
        }
        if (request.getPrice() == null) {
            throw new InvalidFuneralRequestException("Стоимость заявки обязательна");
        }
        if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidFuneralRequestException("Стоимость заявки не может быть отрицательной");
        }
        if (request.getComment() != null && request.getComment().length() > MAX_COMMENT_LENGTH) {
            throw new InvalidFuneralRequestException(
                    "Комментарий не должен превышать " + MAX_COMMENT_LENGTH + " символов"
            );
        }
    }

    private static void rejectPastDate(LocalDate ceremonyDate) {
        if (ceremonyDate.isBefore(LocalDate.now())) {
            throw new InvalidFuneralRequestException("Дата новой церемонии не может находиться в прошлом");
        }
    }

    public static void validateStatusTransition(RequestStatus current, RequestStatus next) {
        if (current == null || next == null) {
            throw new InvalidFuneralRequestException("Статус заявки обязателен");
        }

        boolean forbidden = (current == RequestStatus.NEW && next == RequestStatus.COMPLETED)
                || (current == RequestStatus.COMPLETED && next == RequestStatus.CANCELLED)
                || (current == RequestStatus.CANCELLED && next == RequestStatus.IN_PROGRESS);

        if (forbidden) {
            throw new InvalidFuneralRequestException(
                    "Недопустимый переход статуса: " + current + " -> " + next
            );
        }
    }

    private static String normalizeSpaces(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
