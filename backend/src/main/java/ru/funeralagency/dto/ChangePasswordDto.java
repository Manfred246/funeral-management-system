package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDto(
        @NotBlank(message = "Текущий пароль обязателен")
        @Size(max = 72, message = "Текущий пароль слишком длинный") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String currentPassword,
        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 8, max = 72, message = "Новый пароль должен содержать от 8 до 72 символов") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String newPassword
) {
}
