package ru.funeralagency.service;

import ru.funeralagency.exception.DatabaseException;
import org.springframework.stereotype.Service;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.validation.ClientValidationRules;

import java.util.List;

/** Бизнес-логика работы с клиентами. */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final FuneralRequestRepository requestRepository;

    public ClientService(
            ClientRepository clientRepository,
            FuneralRequestRepository requestRepository
    ) {
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
    }

    public Client create(Client client) {
        ClientValidationRules.validateAndNormalize(client);
        client.setId(null);
        try {
            return clientRepository.create(client);
        } catch (DatabaseException exception) {
            throwIfDuplicateContact(exception);
            throw exception;
        }
    }

    public Client findById(Long id) {
        validateId(id);
        return clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с ID " + id + " не найден"));
    }

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    public Client update(Long id, Client client) {
        validateId(id);
        ClientValidationRules.validateAndNormalize(client);

        Client existingClient = findById(id);
        existingClient.setFullName(client.getFullName());
        existingClient.setPhone(client.getPhone());
        existingClient.setEmail(client.getEmail());
        try {
            return clientRepository.update(existingClient);
        } catch (DatabaseException exception) {
            throwIfDuplicateContact(exception);
            throw exception;
        }
    }

    public void deleteById(Long id) {
        validateId(id);
        // Проверка до удаления позволяет вернуть корректный 404 вместо успешного DELETE для отсутствующего ID.
        findById(id);
        if (requestRepository.existsByClientId(id)) {
            throw new IllegalStateException("Нельзя удалить клиента с ID " + id + ": у него есть связанные заявки");
        }
        try {
            clientRepository.deleteById(id);
        } catch (DatabaseException exception) {
            // Защищает от гонки, если связанная заявка появилась после предварительной проверки.
            if ("23503".equals(exception.getSqlState()) || "23001".equals(exception.getSqlState())) {
                throw new IllegalStateException(
                        "Нельзя удалить клиента с ID " + id + ": у него есть связанные заявки",
                        exception
                );
            }
            throw exception;
        }
    }

    private void throwIfDuplicateContact(DatabaseException exception) {
        if ("23505".equals(exception.getSqlState())) {
            throw new IllegalStateException("Клиент с таким телефоном или email уже существует", exception);
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID клиента должен быть положительным числом");
        }
    }
}
