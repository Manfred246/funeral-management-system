package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenDto(
        @NotBlank(message = "Refresh token обязателен")
        @Size(max = 128, message = "Некорректный refresh token") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String refreshToken
) {
}
