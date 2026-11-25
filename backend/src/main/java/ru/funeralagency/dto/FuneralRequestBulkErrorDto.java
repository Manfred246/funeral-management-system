package ru.funeralagency.dto;

import java.util.UUID;

public record FuneralRequestBulkErrorDto(UUID uuid, String message) {
}
