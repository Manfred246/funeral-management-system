package ru.funeralagency.exception;

/** Ошибка проверки данных, полученных от пользователя. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
