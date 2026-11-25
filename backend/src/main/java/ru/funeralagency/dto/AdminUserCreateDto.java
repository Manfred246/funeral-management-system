package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.funeralagency.model.Role;

import java.util.Set;

public record AdminUserCreateDto(@NotBlank(message = "Логин обязателен") @Size(max = 100, message = "Логин не длиннее 100 символов") String login,
                                 @NotBlank(message = "Имя обязательно") @Size(max = 255, message = "Имя не длиннее 255 символов") String name,
                                 @NotEmpty(message = "Укажите хотя бы одну роль") Set<@NotNull(message = "Роль не может быть null") Role> roles,
                                 @NotBlank(message = "Временный пароль обязателен")
                                 @Size(min = 8, max = 72, message = "Временный пароль должен содержать от 8 до 72 символов") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String temporaryPassword) {
    @Override
    public String toString() {
        return "AdminUserCreateDto[credentials hidden]";
    }
}
