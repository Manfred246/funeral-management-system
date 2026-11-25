package ru.funeralagency.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.funeralagency.model.Role;

import java.util.Set;

public record AdminUserRolesDto(@NotEmpty(message = "Укажите хотя бы одну роль") Set<@NotNull(message = "Роль не может быть null") Role> roles) {
}
