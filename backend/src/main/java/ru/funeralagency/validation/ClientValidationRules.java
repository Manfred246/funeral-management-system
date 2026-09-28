package ru.funeralagency.validation;

import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.Client;

/** Проверки выполняются сервисом перед созданием и изменением клиента. */
public final class ClientValidationRules {
    private ClientValidationRules() {
    }

    public static void validateAndNormalize(Client client) {
        if (client == null) {
            throw new ValidationException("Данные клиента обязательны");
        }
        String fullName = client.getFullName();
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("ФИО клиента обязательно");
        }
        fullName = fullName.trim().replaceAll("\\s+", " ");
        if (fullName.length() > 255) {
            throw new ValidationException("ФИО клиента не должно превышать 255 символов");
        }
        String phone = normalizePhone(client.getPhone());
        String email = client.getEmail();
        if (email != null) {
            email = email.trim();
            if (email.isEmpty()) {
                email = null;
            } else if (email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ValidationException("Некорректный email");
            }
        }
        client.setFullName(fullName);
        client.setPhone(phone);
        client.setEmail(email);
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException("Телефон клиента обязателен");
        }
        phone = phone.trim();
        // Проверяем исходную строку, чтобы не скрыть ошибочные символы при очистке.
        if (!phone.matches("\\+?[0-9][0-9 ()-]*") || !hasBalancedParentheses(phone)) {
            throw new ValidationException("Некорректный формат телефона");
        }
        String normalized = phone.replaceAll("[ ()-]", "");
        if (!normalized.matches("\\+?[0-9]{10,15}")) {
            throw new ValidationException("Телефон должен содержать от 10 до 15 цифр");
        }
        // Максимум 16 символов вместе с '+': помещается в VARCHAR(20).
        return normalized;
    }

    private static boolean hasBalancedParentheses(String phone) {
        int balance = 0;
        for (int i = 0; i < phone.length(); i++) {
            if (phone.charAt(i) == '(') {
                balance++;
            } else if (phone.charAt(i) == ')') {
                balance--;
                if (balance < 0) {
                    return false;
                }
            }
        }
        return balance == 0 && !phone.contains("()");
    }
}
