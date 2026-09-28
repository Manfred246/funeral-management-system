package ru.funeralagency.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.Client;

import static org.junit.jupiter.api.Assertions.*;

class ClientValidationRulesTest {
    @Test
    void normalizesNamePhoneAndEmptyEmail() {
        Client client = new Client(null, "  Иванов   Иван  ", " +7 (999) 123 - 45 - 67 ", "   ");
        ClientValidationRules.validateAndNormalize(client);
        assertEquals("Иванов Иван", client.getFullName());
        assertEquals("+79991234567", client.getPhone());
        assertNull(client.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Иван", "Анна Мария де ла Крус"})
    void acceptsNamesWithoutArtificialWordLimit(String fullName) {
        Client client = new Client(null, fullName, "+79991234567", null);
        assertDoesNotThrow(() -> ClientValidationRules.validateAndNormalize(client));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123-45", "+7 (999))(1234567", "+7 (9991234567", "+7 ()9991234567",
            "+79991234567abc", "++79991234567", "+1234567890123456", "   "})
    void rejectsInvalidPhones(String phone) {
        Client client = new Client(null, "Иван Иванов", phone, null);
        assertThrows(ValidationException.class, () -> ClientValidationRules.validateAndNormalize(client));
    }

    @Test
    void rejectsBlankAndOverlongNames() {
        Client client = new Client(null, "   ", "+79991234567", null);
        assertThrows(ValidationException.class, () -> ClientValidationRules.validateAndNormalize(client));
        client.setFullName("Я".repeat(256));
        assertThrows(ValidationException.class, () -> ClientValidationRules.validateAndNormalize(client));
        client.setFullName("Я".repeat(255));
        assertDoesNotThrow(() -> ClientValidationRules.validateAndNormalize(client));
    }

    @Test
    void rejectsMalformedEmailAndMissingClient() {
        Client client = new Client(null, "Иван Иванов", "+79991234567", "ivan@example");
        assertThrows(ValidationException.class, () -> ClientValidationRules.validateAndNormalize(client));
        assertThrows(ValidationException.class, () -> ClientValidationRules.validateAndNormalize(null));
    }
}
