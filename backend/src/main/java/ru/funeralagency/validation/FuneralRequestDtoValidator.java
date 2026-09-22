package ru.funeralagency.validation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.exception.InvalidFuneralRequestException;
import ru.funeralagency.model.CeremonyType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Проверяет корректность входных DTO заявки до их преобразования. */
@Component
public class FuneralRequestDtoValidator {

    public void validateForCreate(FuneralRequestCreateDto dto) {
        if (dto == null) {
            throw new InvalidFuneralRequestException("Данные заявки обязательны");
        }
        validateCommon(
                dto.getClientId(),
                dto.getDeceasedFullName(),
                dto.getCeremonyDate(),
                dto.getCeremonyType(),
                dto.getPrice(),
                dto.getComment()
        );
        if (dto.getCeremonyDate().isBefore(LocalDate.now())) {
            throw new InvalidFuneralRequestException(
                    "Дата новой церемонии не может находиться в прошлом"
            );
        }
    }

    public void validateForUpdate(FuneralRequestUpdateDto dto) {
        if (dto == null) {
            throw new InvalidFuneralRequestException("Данные заявки обязательны");
        }
        validateCommon(
                dto.getClientId(),
                dto.getDeceasedFullName(),
                dto.getCeremonyDate(),
                dto.getCeremonyType(),
                dto.getPrice(),
                dto.getComment()
        );
        if (dto.getStatus() == null) {
            throw new InvalidFuneralRequestException("Статус заявки обязателен");
        }
    }

    private void validateCommon(
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyType ceremonyType,
            BigDecimal price,
            String comment
    ) {
        if (clientId == null || clientId <= 0) {
            throw new InvalidFuneralRequestException("ID клиента должен быть положительным числом");
        }
        String normalizedName = normalizeSpaces(deceasedFullName);
        if (!StringUtils.hasText(normalizedName)) {
            throw new InvalidFuneralRequestException("ФИО умершего обязательно");
        }
        if (normalizedName.length() > FuneralRequestValidationRules.MAX_DECEASED_FULL_NAME_LENGTH) {
            throw new InvalidFuneralRequestException(
                    "ФИО умершего не должно превышать "
                            + FuneralRequestValidationRules.MAX_DECEASED_FULL_NAME_LENGTH
                            + " символов"
            );
        }
        if (ceremonyDate == null) {
            throw new InvalidFuneralRequestException("Дата церемонии обязательна");
        }
        if (ceremonyType == null) {
            throw new InvalidFuneralRequestException("Тип церемонии обязателен");
        }
        if (price == null) {
            throw new InvalidFuneralRequestException("Стоимость заявки обязательна");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidFuneralRequestException("Стоимость заявки не может быть отрицательной");
        }
        String normalizedComment = normalizeNullable(comment);
        if (normalizedComment != null
                && normalizedComment.length() > FuneralRequestValidationRules.MAX_COMMENT_LENGTH) {
            throw new InvalidFuneralRequestException(
                    "Комментарий не должен превышать "
                            + FuneralRequestValidationRules.MAX_COMMENT_LENGTH
                            + " символов"
            );
        }
    }

    private String normalizeSpaces(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
