package ru.funeralagency.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.funeralagency.exception.ClientHasRequestsException;
import ru.funeralagency.exception.ClientNotFoundException;
import ru.funeralagency.exception.InvalidClientException;
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
        ClientValidationRules.normalize(client);
        ClientValidationRules.validate(client);
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
        ClientValidationRules.normalize(client);
        ClientValidationRules.validate(client);

        Client existingClient = findById(id);
        existingClient.setFullName(client.getFullName());
        existingClient.setPhone(client.getPhone());
        existingClient.setEmail(client.getEmail());
        clientRepository.update(existingClient);
        return existingClient;
    }

    public void deleteById(Long id) {
        validateId(id);
        // Проверка до удаления позволяет вернуть корректный 404 вместо успешного DELETE для отсутствующего ID.
        findById(id);
        if (requestRepository.existsByClientId(id)) {
            throw new ClientHasRequestsException(id);
        }
        try {
            clientRepository.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            // Защищает от гонки, если связанная заявка появилась после предварительной проверки.
            throw new ClientHasRequestsException(id, exception);
        }
    }

    public void delete(Long id) {
        deleteById(id);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidClientException("ID клиента должен быть положительным числом");
        }
    }
}
