package ru.funeralagency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Возникает, если клиент с указанным идентификатором отсутствует. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(Long id) {
        super("Клиент с ID " + id + " не найден");
    }
}
