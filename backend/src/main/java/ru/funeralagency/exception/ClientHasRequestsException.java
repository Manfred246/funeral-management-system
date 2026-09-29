package ru.funeralagency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Возникает при попытке удалить клиента, на которого ссылаются заявки. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ClientHasRequestsException extends RuntimeException {

    public ClientHasRequestsException(Long id) {
        super(message(id));
    }

    public ClientHasRequestsException(Long id, Throwable cause) {
        super(message(id), cause);
    }

    private static String message(Long id) {
        return "Нельзя удалить клиента с ID " + id + ": у него есть связанные заявки";
    }
}
