package ru.funeralagency.dto;

import java.util.List;

public record CsvApplyDto(List<CsvImportSuccessDto> successful, List<CsvRowErrorDto> errors) {
}
