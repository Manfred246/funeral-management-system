package ru.funeralagency.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.funeralagency.dto.ApiErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiExceptionHandlerTest {
    @Test
    void hidesPersistenceErrorDetails() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        var response = handler.handleDatabaseError(
                new org.springframework.dao.DataAccessResourceFailureException("SQL and internals"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Не удалось выполнить операцию с базой данных", response.getBody().message());
    }


    @Test
    void hidesUnexpectedErrorDetails() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedError(
                new RuntimeException("Технические подробности"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Внутренняя ошибка сервера", response.getBody().message());
        assertEquals("/api/test", response.getBody().path());
    }
}
