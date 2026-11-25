package ru.funeralagency.service;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.validation.ClientValidationRules;

import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;
    private final FuneralRequestRepository requestRepository;

    public ClientService(ClientRepository clientRepository, FuneralRequestRepository requestRepository) {
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public Client create(Client client) {
        ClientValidationRules.validateAndNormalize(client);
        client.setId(null);
        try {
            // Flush нужен здесь, чтобы конфликт уникальности возник до выхода из сервиса.
            return clientRepository.saveAndFlush(client);
        } catch (DataIntegrityViolationException exception) {
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
        return clientRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public Client update(Long id, Client client) {
        validateId(id);
        ClientValidationRules.validateAndNormalize(client);
        Client existing = findById(id);
        existing.setFullName(client.getFullName());
        existing.setPhone(client.getPhone());
        existing.setEmail(client.getEmail());
        try {
            return clientRepository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException exception) {
            throwIfDuplicateContact(exception);
            throw exception;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        Client client = findById(id);
        if (requestRepository.existsByClientId(id)) {
            throw new BusinessException(clientHasRequestsMessage(id));
        }
        try {
            clientRepository.delete(client);
            clientRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // FK защищает и от заявки, созданной после предварительной проверки.
            if (hasConstraint(exception, "fk_funeral_requests_client")) {
                throw new BusinessException(clientHasRequestsMessage(id), exception);
            }
            throw exception;
        }
    }

    private void throwIfDuplicateContact(DataIntegrityViolationException exception) {
        if (hasConstraint(exception, "uq_clients_phone") || hasConstraint(exception, "uq_clients_email")) {
            throw new BusinessException("Клиент с таким телефоном или email уже существует", exception);
        }
    }

    private boolean hasConstraint(Throwable exception, String name) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) {
                return true;
            }
            // Hibernate может не извлечь имя из локализованного сообщения PostgreSQL.
            if (cause instanceof PSQLException postgres && postgres.getServerErrorMessage() != null
                    && name.equals(postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("ID клиента должен быть положительным числом");
        }
    }

    private String clientHasRequestsMessage(Long id) {
        return "Нельзя удалить клиента с ID " + id + ": у него есть связанные заявки";
    }
}
