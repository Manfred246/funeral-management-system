package ru.funeralagency.validation;

import org.springframework.util.StringUtils;
import ru.funeralagency.exception.InvalidClientException;
import ru.funeralagency.model.Client;

import java.util.regex.Pattern;

/** Единые правила проверки и нормализации контактных данных клиента. */
public final class ClientValidationRules {

    /** Практический предел длины полного имени в пользовательской форме. */
    public static final int MAX_FULL_NAME_LENGTH = 100;

    /** До 15 цифр международного номера с запасом на пробелы, скобки и дефисы. */
    public static final int MAX_PHONE_LENGTH = 25;

    /** Практический максимальный размер email-адреса согласно ограничениям почтовых систем. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** ФИО состоит из двух или трёх слов; разрешены буквы, дефис и апостроф. */
    public static final String FULL_NAME_REGEXP =
            "^[\\p{L}]+(?:['’\\-][\\p{L}]+)*(?:\\s+[\\p{L}]+(?:['’\\-][\\p{L}]+)*){1,2}$";

    /** Телефон может содержать международный префикс и разделители, но должен иметь 10–15 цифр. */
    public static final String PHONE_REGEXP =
            "^(?=(?:\\D*\\d){10,15}\\D*$)\\+?[0-9][0-9 ()-]*$";

    /** Email должен иметь локальную часть, домен и точку в домене. */
    public static final String EMAIL_REGEXP = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private static final Pattern FULL_NAME_PATTERN = Pattern.compile(FULL_NAME_REGEXP);
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEXP);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEXP);

    private ClientValidationRules() {
    }

    /** Приводит данные к единому виду перед сохранением. */
    public static void normalize(Client client) {
        if (client == null) {
            return;
        }
        client.setFullName(normalizeSpaces(client.getFullName()));
        client.setPhone(normalizeTrimmed(client.getPhone()));
        client.setEmail(normalizeNullable(client.getEmail()));
    }

    /** Проверяет бизнес-правила клиента независимо от способа вызова сервиса. */
    public static void validate(Client client) {
        if (client == null) {
            throw new InvalidClientException("Клиент не может быть null");
        }

        String fullName = normalizeSpaces(client.getFullName());
        if (!StringUtils.hasText(fullName)) {
            throw new InvalidClientException("ФИО клиента обязательно");
        }
        if (fullName.length() > MAX_FULL_NAME_LENGTH) {
            throw new InvalidClientException(
                    "ФИО клиента не должно превышать " + MAX_FULL_NAME_LENGTH + " символов"
            );
        }
        if (!FULL_NAME_PATTERN.matcher(fullName).matches()) {
            throw new InvalidClientException(
                    "ФИО должно содержать от двух до трёх слов и только буквы, дефис или апостроф"
            );
        }

        String phone = normalizeTrimmed(client.getPhone());
        if (!StringUtils.hasText(phone)) {
            throw new InvalidClientException("Телефон клиента обязателен");
        }
        if (phone.length() > MAX_PHONE_LENGTH) {
            throw new InvalidClientException(
                    "Телефон клиента не должен превышать " + MAX_PHONE_LENGTH + " символов"
            );
        }
        if (!PHONE_PATTERN.matcher(phone).matches() || !hasBalancedParentheses(phone)) {
            throw new InvalidClientException(
                    "Телефон должен содержать от 10 до 15 цифр и допустимые разделители"
            );
        }

        String email = normalizeNullable(client.getEmail());
        if (email != null) {
            if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new InvalidClientException("Некорректный email");
            }
        }
    }

    private static boolean hasBalancedParentheses(String value) {
        int opening = 0;
        int closing = 0;
        int openingIndex = -1;
        int closingIndex = -1;

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '(') {
                opening++;
                openingIndex = openingIndex == -1 ? i : openingIndex;
            } else if (character == ')') {
                closing++;
                closingIndex = closingIndex == -1 ? i : closingIndex;
            }
        }

        return opening == closing
                && (opening == 0 || openingIndex < closingIndex)
                && !value.contains("()");
    }

    private static String normalizeSpaces(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeTrimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeNullable(String value) {
        String normalized = normalizeTrimmed(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
