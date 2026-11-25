package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetDto(@NotBlank(message = "Временный пароль обязателен")
                                    @Size(min = 8, max = 72, message = "Временный пароль должен содержать от 8 до 72 символов") String temporaryPassword) {
    @Override
    public String toString() {
        return "AdminPasswordResetDto[credentials hidden]";
    }
}
