package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserEditDto(@NotBlank(message = "Логин обязателен") @Size(max = 100, message = "Логин не длиннее 100 символов") String login,
                               @NotBlank(message = "Имя обязательно") @Size(max = 255, message = "Имя не длиннее 255 символов") String name) {
}
