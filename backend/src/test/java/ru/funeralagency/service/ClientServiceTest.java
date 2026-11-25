package ru.funeralagency.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {
    @Mock private ClientRepository clients;
    @Mock private FuneralRequestRepository requests;
    private ClientService service;

    @BeforeEach
    void setUp() {
        service = new ClientService(clients, requests);
    }

    @Test
    void createNormalizesDataAndReturnsSavedClient() {
        Client input = new Client(99L, "  Иван   Иванов ", "+7 (999) 123-45-67", " ");
        Client saved = new Client(7L, "Иван Иванов", "+79991234567", null);
        when(clients.saveAndFlush(input)).thenReturn(saved);
        assertSame(saved, service.create(input));
        assertNull(input.getId());
        assertEquals("Иван Иванов", input.getFullName());
        assertEquals("+79991234567", input.getPhone());
        assertNull(input.getEmail());
    }

    @Test
    void invalidDataNeverReachesRepository() {
        assertThrows(ValidationException.class,
                () -> service.create(new Client(null, "Иван Иванов", "123", null)));
        verifyNoInteractions(clients);
    }

    @Test
    void updateUsesStoredIdAndReturnsRepositoryResult() {
        Client existing = new Client(7L, "Иван Иванов", "+79991234567", null);
        Client input = new Client(99L, "Пётр Петров", "+7 (999) 222-33-44", null);
        Client saved = new Client(7L, "Пётр Петров", "+79992223344", null);
        when(clients.findById(7L)).thenReturn(Optional.of(existing));
        when(clients.saveAndFlush(existing)).thenReturn(saved);
        assertSame(saved, service.update(7L, input));
        assertEquals(7L, existing.getId());
        assertEquals("+79992223344", existing.getPhone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uq_clients_phone", "uq_clients_email"})
    void duplicateContactBecomesBusinessConflict(String constraint) {
        DataIntegrityViolationException failure = constraintError(constraint);
        when(clients.saveAndFlush(any())).thenThrow(failure);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new Client(null, "Иван Иванов", "+79991234567", null)));
        assertEquals("Клиент с таким телефоном или email уже существует", error.getMessage());
        assertSame(failure, error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uq_clients_phone", "uq_clients_email"})
    void duplicateContactOnUpdateBecomesBusinessConflict(String constraint) {
        Client existing = new Client(7L, "Иван Иванов", "+79991234567", null);
        when(clients.findById(7L)).thenReturn(Optional.of(existing));
        DataIntegrityViolationException failure = constraintError(constraint);
        when(clients.saveAndFlush(existing)).thenThrow(failure);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(7L, new Client(null, "Пётр Петров", "+79992223344", "test@example.com")));
        assertEquals("Клиент с таким телефоном или email уже существует", error.getMessage());
        assertSame(failure, error.getCause());
    }

    @Test
    void missingClientCannotBeDeleted() {
        when(clients.findById(7L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.deleteById(7L));
        verify(clients, never()).delete(any());
    }

    @Test
    void linkedClientCannotBeDeleted() {
        when(clients.findById(7L)).thenReturn(Optional.of(new Client()));
        when(requests.existsByClientId(7L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.deleteById(7L));
        verify(clients, never()).delete(any());
    }

    @Test
    void foreignKeyRaceBecomesConflictButConnectionErrorIsPreserved() {
        when(clients.findById(7L)).thenReturn(Optional.of(new Client()));
        doThrow(constraintError("fk_funeral_requests_client")).when(clients).flush();
        assertThrows(BusinessException.class, () -> service.deleteById(7L));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("test");
        doThrow(failure).when(clients).flush();
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.deleteById(7L)));
    }

    private DataIntegrityViolationException constraintError(String name) {
        return new DataIntegrityViolationException("test",
                new ConstraintViolationException("test", new SQLException("test"), name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"uq_clients_phone", "uq_clients_email", "fk_funeral_requests_client"})
    void localizedPostgresErrorUsesStructuredConstraintWhenHibernateNameIsMissing(String constraint) {
        String state = constraint.startsWith("fk_") ? "23001" : "23505";
        var metadata = new ServerErrorMessage("SERROR\0C" + state + "\0MЛокализованная ошибка\0n" + constraint + "\0");
        var postgres = new PSQLException(metadata);
        var failure = new DataIntegrityViolationException("test",
                new ConstraintViolationException("test", postgres, null));
        if (constraint.startsWith("fk_")) {
            when(clients.findById(7L)).thenReturn(Optional.of(new Client()));
            doThrow(failure).when(clients).flush();
            BusinessException error = assertThrows(BusinessException.class, () -> service.deleteById(7L));
            assertTrue(error.getMessage().contains("связанные заявки"));
        } else {
            when(clients.saveAndFlush(any())).thenThrow(failure);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.create(new Client(null, "Иван Иванов", "+79991234567", null)));
            assertEquals("Клиент с таким телефоном или email уже существует", error.getMessage());
        }
    }
}
