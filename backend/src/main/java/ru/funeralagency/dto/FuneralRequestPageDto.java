package ru.funeralagency.dto;

import java.util.List;

public record FuneralRequestPageDto(
        List<FuneralRequestResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
