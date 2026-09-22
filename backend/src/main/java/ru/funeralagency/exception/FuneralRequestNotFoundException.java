package ru.funeralagency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Возникает, если заявка с указанным идентификатором отсутствует. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class FuneralRequestNotFoundException extends RuntimeException {

    public FuneralRequestNotFoundException(Long id) {
        super("Заявка с ID " + id + " не найдена");
    }
}
