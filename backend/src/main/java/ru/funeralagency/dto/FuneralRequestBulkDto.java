package ru.funeralagency.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.funeralagency.model.RequestStatus;

import java.util.List;
import java.util.UUID;

public record FuneralRequestBulkDto(
        @NotNull(message = "Укажите массовую операцию") BulkOperation operation,
        @NotEmpty(message = "Укажите UUID заявок") List<@NotNull(message = "UUID заявки обязателен") UUID> uuids,
        RequestStatus status,
        Long tagId
) {
}
