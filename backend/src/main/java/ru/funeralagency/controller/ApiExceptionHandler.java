package ru.funeralagency.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.funeralagency.dto.ApiErrorResponse;
import ru.funeralagency.exception.ClientHasRequestsException;
import ru.funeralagency.exception.ClientNotFoundException;
import ru.funeralagency.exception.FuneralRequestNotFoundException;
import ru.funeralagency.exception.InvalidClientException;
import ru.funeralagency.exception.InvalidFuneralRequestException;

import java.time.Instant;

/** Преобразует доменные исключения в стабильные и понятные ответы REST API. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidClientException.class, InvalidFuneralRequestException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationError(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler({ClientNotFoundException.class, FuneralRequestNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ClientHasRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ClientHasRequestsException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Некорректное тело запроса", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Некорректное значение параметра: " + exception.getName(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
