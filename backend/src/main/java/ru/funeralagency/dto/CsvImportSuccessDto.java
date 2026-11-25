package ru.funeralagency.dto;

import java.util.UUID;

public record CsvImportSuccessDto(long line, UUID uuid, String requestCode, boolean created) {
}
