package ru.funeralagency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Возникает при нарушении бизнес-правил клиента. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidClientException extends IllegalArgumentException {

    public InvalidClientException(String message) {
        super(message);
    }
}
