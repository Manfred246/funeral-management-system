package ru.funeralagency.security;

import ru.funeralagency.exception.ValidationException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException("Пароль должен содержать не менее 8 символов и не более 72 байт UTF-8");
        }
    }
}
