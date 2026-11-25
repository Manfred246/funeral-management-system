package ru.funeralagency.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RequestNoteResponseDto(
        UUID uuid,
        UUID authorUuid,
        String authorName,
        String text,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
