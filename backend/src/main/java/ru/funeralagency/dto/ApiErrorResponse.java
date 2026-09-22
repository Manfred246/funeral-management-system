package ru.funeralagency.dto;

import java.time.Instant;

/** Единый ответ REST API при ошибке. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
