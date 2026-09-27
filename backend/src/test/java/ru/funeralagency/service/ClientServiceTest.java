package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.ClientHasRequestsException;
import ru.funeralagency.exception.ClientNotFoundException;
import ru.funeralagency.exception.DatabaseException;
import ru.funeralagency.exception.InvalidClientException;
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
        when(clients.create(input)).thenReturn(saved);
        assertSame(saved, service.create(input));
        assertNull(input.getId());
        assertEquals("Иван Иванов", input.getFullName());
        assertEquals("+79991234567", input.getPhone());
        assertNull(input.getEmail());
    }

    @Test
    void invalidDataNeverReachesRepository() {
        assertThrows(InvalidClientException.class,
                () -> service.create(new Client(null, "Иван Иванов", "123", null)));
        verifyNoInteractions(clients);
    }

    @Test
    void updateUsesStoredIdAndReturnsRepositoryResult() {
        Client existing = new Client(7L, "Иван Иванов", "+79991234567", null);
        Client input = new Client(99L, "Пётр Петров", "+7 (999) 222-33-44", null);
        Client saved = new Client(7L, "Пётр Петров", "+79992223344", null);
        when(clients.findById(7L)).thenReturn(Optional.of(existing));
        when(clients.update(existing)).thenReturn(saved);
        assertSame(saved, service.update(7L, input));
        assertEquals(7L, existing.getId());
        assertEquals("+79992223344", existing.getPhone());
    }

    @Test
    void duplicateContactBecomesBusinessConflict() {
        when(clients.create(any())).thenThrow(databaseError("23505"));
        assertThrows(BusinessException.class,
                () -> service.create(new Client(null, "Иван Иванов", "+79991234567", null)));
    }

    @Test
    void missingClientCannotBeDeleted() {
        when(clients.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ClientNotFoundException.class, () -> service.deleteById(7L));
        verify(clients, never()).deleteById(anyLong());
    }

    @Test
    void linkedClientCannotBeDeleted() {
        when(clients.findById(7L)).thenReturn(Optional.of(new Client()));
        when(requests.existsByClientId(7L)).thenReturn(true);
        assertThrows(ClientHasRequestsException.class, () -> service.deleteById(7L));
        verify(clients, never()).deleteById(anyLong());
    }

    @Test
    void foreignKeyRaceBecomesConflictButConnectionErrorIsPreserved() {
        when(clients.findById(7L)).thenReturn(Optional.of(new Client()));
        doThrow(databaseError("23503")).when(clients).deleteById(7L);
        assertThrows(ClientHasRequestsException.class, () -> service.deleteById(7L));
        DatabaseException failure = databaseError("08006");
        doThrow(failure).when(clients).deleteById(7L);
        assertSame(failure, assertThrows(DatabaseException.class, () -> service.deleteById(7L)));
    }

    private DatabaseException databaseError(String state) {
        return new DatabaseException("Ошибка БД", new SQLException("test", state));
    }
}