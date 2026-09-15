package ru.funeralagency.repository;

import ru.funeralagency.model.Client;

import java.util.List;
import java.util.Optional;

/** Репозиторий для работы с клиентами. */
public interface ClientRepository {

    Client save(Client client);

    Optional<Client> findById(Long id);

    List<Client> findAll();

    void update(Client client);

    void deleteById(Long id);
}
