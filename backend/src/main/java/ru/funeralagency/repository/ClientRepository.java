package ru.funeralagency.repository;

import ru.funeralagency.model.Client;

import java.util.List;
import java.util.Optional;

public interface ClientRepository {
    Client create(Client client);

    List<Client> findAll();

    Optional<Client> findById(long id);

    Client update(Client client);

    void deleteById(long id);

    boolean existsById(long id);
}
