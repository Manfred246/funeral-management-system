package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetDto(@NotBlank(message = "Временный пароль обязателен")
                                    @Size(min = 8, max = 72, message = "Временный пароль должен содержать от 8 до 72 символов") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String temporaryPassword) {
    @Override
    public String toString() {
        return "AdminPasswordResetDto[credentials hidden]";
    }
}
