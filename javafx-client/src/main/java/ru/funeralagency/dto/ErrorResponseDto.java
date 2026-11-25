package ru.funeralagency.dto;

public record ErrorResponseDto(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
