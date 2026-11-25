package ru.funeralagency.dto;

import ru.funeralagency.model.Role;

import java.util.Set;
import java.util.UUID;

public record AdminUserDto(UUID uuid, String login, String name, Set<Role> roles, boolean enabled,
                           boolean passwordChangeRequired, int failedLoginAttempts) {
}
