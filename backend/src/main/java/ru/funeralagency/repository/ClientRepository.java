package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.funeralagency.model.Client;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findAllByOrderByIdAsc();
}
