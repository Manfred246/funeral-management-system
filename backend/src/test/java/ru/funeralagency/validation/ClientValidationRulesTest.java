package ru.funeralagency.validation;

import org.junit.jupiter.api.Test;
import ru.funeralagency.exception.InvalidClientException;
import ru.funeralagency.model.Client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientValidationRulesTest {

    @Test
    void acceptsValidClientData() {
        Client client = new Client(
                "Иванов Иван Иванович",
                "+7 (999) 123-45-67",
                "ivan@example.com"
        );

        assertDoesNotThrow(() -> ClientValidationRules.validate(client));
    }

    @Test
    void rejectsNameWithoutFullNameStructure() {
        Client client = new Client("Иван", "+7 (999) 123-45-67", null);

        assertThrows(InvalidClientException.class, () -> ClientValidationRules.validate(client));
    }

    @Test
    void rejectsPhoneWithTooFewDigits() {
        Client client = new Client("Иванов Иван", "123-45", null);

        assertThrows(InvalidClientException.class, () -> ClientValidationRules.validate(client));
    }

    @Test
    void rejectsMalformedEmail() {
        Client client = new Client("Иванов Иван", "+7 999 123 45 67", "ivan@example");

        assertThrows(InvalidClientException.class, () -> ClientValidationRules.validate(client));
    }

    @Test
    void normalizesWhitespaceAndEmptyEmail() {
        Client client = new Client("  Иванов   Иван  ", " +7 999 123 45 67 ", "   ");

        ClientValidationRules.normalize(client);

        assertEquals("Иванов Иван", client.getFullName());
        assertEquals("+7 999 123 45 67", client.getPhone());
        assertEquals(null, client.getEmail());
        assertDoesNotThrow(() -> ClientValidationRules.validate(client));
    }
}
