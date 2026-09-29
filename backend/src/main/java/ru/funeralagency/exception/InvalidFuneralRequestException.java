package ru.funeralagency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Возникает при нарушении бизнес-правил заявки. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFuneralRequestException extends IllegalArgumentException {

    public InvalidFuneralRequestException(String message) {
        super(message);
    }
}
