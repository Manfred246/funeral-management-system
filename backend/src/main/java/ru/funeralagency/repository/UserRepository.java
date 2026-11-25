package ru.funeralagency.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    // Сериализует попытки входа одного пользователя, чтобы счётчик не терял обновления.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    Optional<User> findByLogin(String login);

    Optional<User> findByUuid(UUID uuid);
}
