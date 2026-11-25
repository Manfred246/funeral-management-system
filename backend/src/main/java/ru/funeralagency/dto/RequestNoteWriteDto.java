package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestNoteWriteDto(
        @NotBlank(message = "Текст заметки обязателен") String text,
        boolean pinned
) {
}
