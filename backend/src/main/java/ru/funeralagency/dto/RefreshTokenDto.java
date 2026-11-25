package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenDto(
        @NotBlank(message = "Refresh token обязателен")
        @Size(max = 128, message = "Некорректный refresh token") String refreshToken
) {
}
