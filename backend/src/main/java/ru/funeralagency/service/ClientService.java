package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.funeralagency.exception.ClientNotFoundException;
import ru.funeralagency.exception.InvalidClientException;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.ClientRepository;

import java.util.List;

/** Бизнес-логика работы с клиентами. */
@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client create(Client client) {
        validate(client);
        client.setId(null);
        return clientRepository.save(client);
    }

    public Client findById(Long id) {
        validateId(id);
        return clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));
    }

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    public Client update(Long id, Client client) {
        validateId(id);
        validate(client);

        Client existingClient = findById(id);
        existingClient.setFullName(client.getFullName().trim());
        existingClient.setPhone(client.getPhone().trim());
        existingClient.setEmail(normalizeNullable(client.getEmail()));
        clientRepository.update(existingClient);
        return existingClient;
    }

    public void deleteById(Long id) {
        validateId(id);
        // Проверка до удаления позволяет вернуть корректный 404 вместо успешного DELETE для отсутствующего ID.
        findById(id);
        clientRepository.deleteById(id);
    }

    private void validate(Client client) {
        if (client == null) {
            throw new InvalidClientException("Клиент не может быть null");
        }
        if (!StringUtils.hasText(client.getFullName())) {
            throw new InvalidClientException("ФИО клиента обязательно");
        }
        if (client.getFullName().trim().length() > 255) {
            throw new InvalidClientException("ФИО клиента не должно превышать 255 символов");
        }
        if (!StringUtils.hasText(client.getPhone())) {
            throw new InvalidClientException("Телефон клиента обязателен");
        }
        if (client.getPhone().trim().length() > 32) {
            throw new InvalidClientException("Телефон клиента не должен превышать 32 символа");
        }
        if (client.getEmail() != null && client.getEmail().trim().length() > 255) {
            throw new InvalidClientException("Email не должен превышать 255 символов");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidClientException("ID клиента должен быть положительным числом");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
