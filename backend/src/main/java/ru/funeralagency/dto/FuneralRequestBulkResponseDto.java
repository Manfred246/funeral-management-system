package ru.funeralagency.dto;

import java.util.List;
import java.util.UUID;

public record FuneralRequestBulkResponseDto(
        List<UUID> successful,
        List<FuneralRequestBulkErrorDto> errors
) {
}
