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
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.DatabaseException;
import ru.funeralagency.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.io.UncheckedIOException;

import java.time.Instant;

/** Преобразует доменные исключения в стабильные и понятные ответы REST API. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidClientException.class, InvalidFuneralRequestException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationError(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler({ClientNotFoundException.class, FuneralRequestNotFoundException.class,
            EntityNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({ClientHasRequestsException.class, BusinessException.class})
    public ResponseEntity<ApiErrorResponse> handleConflict(
            RuntimeException exception,
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

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseError(
            DatabaseException exception, HttpServletRequest request
    ) {
        logger.error("Ошибка при обращении к БД", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить операцию с базой данных", request);
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ApiErrorResponse> handleExportError(
            UncheckedIOException exception, HttpServletRequest request
    ) {
        logger.error("Ошибка экспорта", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сформировать файл экспорта", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "Не указан параметр: " + exception.getParameterName(), request);
    }
}
