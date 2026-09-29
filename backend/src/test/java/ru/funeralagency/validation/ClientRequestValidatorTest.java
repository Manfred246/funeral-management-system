package ru.funeralagency.validation;

import org.junit.jupiter.api.Test;
import ru.funeralagency.dto.ClientRequestDto;
import ru.funeralagency.exception.InvalidClientException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestValidatorTest {

    private final ClientRequestValidator validator = new ClientRequestValidator();

    @Test
    void acceptsValidDto() {
        ClientRequestDto dto = new ClientRequestDto(
                "Иванов Иван Иванович",
                "+7 999 123 45 67",
                "ivan@example.com"
        );

        assertDoesNotThrow(() -> validator.validate(dto));
    }

    @Test
    void rejectsInvalidDto() {
        ClientRequestDto dto = new ClientRequestDto("Иван", "123", "invalid-email");

        assertThrows(InvalidClientException.class, () -> validator.validate(dto));
        assertThrows(InvalidClientException.class, () -> validator.validate(null));
    }
}
