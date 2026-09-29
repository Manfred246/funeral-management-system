package ru.funeralagency.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import java.util.regex.Pattern;

/** Читает и проверяет значения, введённые пользователем в консоли. */
class ConsoleInput {

    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()-]*$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Scanner scanner;

    ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    String read(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    long readPositiveLong(String prompt) {
        return readPositiveLong(prompt, "");
    }

    long readPositiveLong(String prompt, String current) {
        while (true) {
            String entered = readWithDefault(prompt, current);
            try {
                long value = Long.parseLong(entered);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ниже выводится общее сообщение для всех неверных значений.
            }
            System.out.println("Введите целое положительное число.");
        }
    }

    String readRequiredText(String prompt, String current, int maxLength) {
        while (true) {
            String entered = readWithDefault(prompt, current);
            if (!entered.isBlank() && entered.length() <= maxLength) {
                return entered;
            }
            System.out.println(
                    "Поле обязательно и не должно превышать " + maxLength + " символов."
            );
        }
    }

    String readPhone(String current) {
        while (true) {
            String phone = readWithDefault("Телефон: ", current);
            if (validPhone(phone)) {
                return phone;
            }
            System.out.println("Введите телефон с 10–15 цифрами, например +79991234567.");
        }
    }

    String readEmail(String current, boolean updating) {
        while (true) {
            String email = readOptionalText("Email", current, updating);
            if (email.isEmpty() || email.length() <= 255 && EMAIL.matcher(email).matches()) {
                return email;
            }
            System.out.println("Введите корректный email или оставьте поле пустым.");
        }
    }

    String readCeremonyDate(String current) {
        while (true) {
            String entered = readWithDefault("Дата церемонии (ГГГГ-ММ-ДД): ", current);
            if (validDate(entered)) {
                LocalDate date = LocalDate.parse(entered);
                if (entered.equals(current) || !date.isBefore(LocalDate.now())) {
                    return entered;
                }
            }
            System.out.println(
                    "Введите дату в формате ГГГГ-ММ-ДД, не раньше сегодняшнего дня."
            );
        }
    }

    String readOptionalDate(String prompt) {
        while (true) {
            String entered = read(prompt);
            if (entered.isEmpty() || validDate(entered)) {
                return entered;
            }
            System.out.println("Введите дату в формате ГГГГ-ММ-ДД.");
        }
    }

    BigDecimal readPrice(String current) {
        while (true) {
            String entered = readWithDefault("Стоимость: ", current);
            try {
                BigDecimal price = new BigDecimal(entered);
                if (price.compareTo(BigDecimal.ZERO) >= 0
                        && price.compareTo(MAX_PRICE) <= 0
                        && price.stripTrailingZeros().scale() <= 2) {
                    return price;
                }
            } catch (NumberFormatException ignored) {
                // Ниже выводится одно сообщение для всех неверных цен.
            }
            System.out.println(
                    "Введите сумму от 0 до 9999999999.99 с точностью до копейки."
            );
        }
    }

    String readCeremonyType(String current) {
        while (true) {
            String entered = readWithDefault(
                    "Тип (BURIAL — захоронение, CREMATION — кремация): ",
                    current
            );
            if (validCeremonyType(entered)) {
                return entered.toUpperCase();
            }
            System.out.println("Введите BURIAL или CREMATION.");
        }
    }

    String readOptionalCeremonyType() {
        while (true) {
            String entered = read("Тип (BURIAL, CREMATION; пусто — любой): ");
            if (entered.isEmpty()) {
                return "";
            }
            if (validCeremonyType(entered)) {
                return entered.toUpperCase();
            }
            System.out.println("Введите BURIAL или CREMATION.");
        }
    }

    String readStatus(String current) {
        while (true) {
            String entered = readWithDefault(
                    "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED): ",
                    current
            );
            if (validStatus(entered)) {
                return entered.toUpperCase();
            }
            System.out.println("Введите статус из списка.");
        }
    }

    String readOptionalStatus() {
        while (true) {
            String entered = read(
                    "Статус (NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED; пусто — любой): "
            );
            if (entered.isEmpty()) {
                return "";
            }
            if (validStatus(entered)) {
                return entered.toUpperCase();
            }
            System.out.println("Введите статус из списка.");
        }
    }

    String readOptionalText(String label, String current, boolean updating) {
        if (!updating) {
            return read(label + " (можно оставить пустым): ");
        }
        String entered = read(
                label + " [" + current + "] (Enter — оставить, - — очистить): "
        );
        if (entered.equals("-")) {
            return "";
        }
        if (entered.isEmpty()) {
            return current;
        }
        return entered;
    }

    private String readWithDefault(String prompt, String current) {
        String suffix = current.isEmpty() ? "" : " [" + current + "]";
        String entered = read(prompt.stripTrailing() + suffix + " ");
        if (entered.isEmpty()) {
            return current;
        }
        return entered;
    }

    private boolean validDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean validPhone(String phone) {
        if (phone.length() > 25 || !PHONE.matcher(phone).matches() || phone.contains("()")) {
            return false;
        }

        int balance = 0;
        for (int index = 0; index < phone.length(); index++) {
            char character = phone.charAt(index);
            if (character == '(') {
                balance++;
            } else if (character == ')') {
                balance--;
                if (balance < 0) {
                    return false;
                }
            }
        }

        String normalized = phone.replaceAll("[ ()-]", "");
        return balance == 0 && normalized.matches("\\+?[0-9]{10,15}");
    }

    private boolean validCeremonyType(String text) {
        return text.equalsIgnoreCase("BURIAL") || text.equalsIgnoreCase("CREMATION");
    }

    private boolean validStatus(String text) {
        return text.equalsIgnoreCase("NEW")
                || text.equalsIgnoreCase("CONFIRMED")
                || text.equalsIgnoreCase("IN_PROGRESS")
                || text.equalsIgnoreCase("COMPLETED")
                || text.equalsIgnoreCase("CANCELLED");
    }
}
