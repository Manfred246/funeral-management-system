package ru.funeralagency.controller;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.funeralagency.dto.ApiErrorResponse;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;

import java.io.UncheckedIOException;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationError(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Неверные учётные данные или недействительный токен", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(
            MethodArgumentNotValidException exception, HttpServletRequest request
    ) {
        var fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "Некорректные данные запроса" : fieldError.getDefaultMessage();
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleOptimisticConflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                "Запись была изменена другим пользователем. Обновите данные и повторите попытку.", request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(
            ValidationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            BusinessException exception,
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrityError(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Операция нарушает ограничения связанных данных", request);
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    public ResponseEntity<ApiErrorResponse> handleDatabaseError(
            RuntimeException exception, HttpServletRequest request
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

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingFile(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Укажите CSV файл в поле file", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingResource(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Ресурс не найден", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        logger.error("Непредвиденная ошибка сервера", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка сервера",
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
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(body);
    }
}
