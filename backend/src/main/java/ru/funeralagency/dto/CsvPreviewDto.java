package ru.funeralagency.dto;

import java.util.List;

public record CsvPreviewDto(int validCount, int errorCount, List<CsvRowErrorDto> errors) {
}
