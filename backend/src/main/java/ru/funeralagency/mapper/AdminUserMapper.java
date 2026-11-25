package ru.funeralagency.mapper;

import ru.funeralagency.dto.AdminUserDto;
import ru.funeralagency.model.User;

import java.util.Set;

public final class AdminUserMapper {
    private AdminUserMapper() {
    }

    public static AdminUserDto toDto(User user) {
        return new AdminUserDto(user.getUuid(), user.getLogin(), user.getName(), Set.copyOf(user.getRoles()),
                user.isEnabled(), user.isPasswordChangeRequired(), user.getFailedLoginAttempts());
    }
}
