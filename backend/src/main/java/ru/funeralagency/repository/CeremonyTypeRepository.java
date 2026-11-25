package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.funeralagency.model.CeremonyType;

import java.util.Optional;

public interface CeremonyTypeRepository extends JpaRepository<CeremonyType, Long> {
    Optional<CeremonyType> findByCode(String code);

    boolean existsByCode(String code);
}
