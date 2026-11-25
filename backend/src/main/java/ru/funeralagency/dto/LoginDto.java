package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDto(
        @NotBlank(message = "Логин обязателен") @Size(max = 100, message = "Логин слишком длинный") String login,
        @NotBlank(message = "Пароль обязателен") @Size(max = 72, message = "Пароль слишком длинный") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String password
) {
}
