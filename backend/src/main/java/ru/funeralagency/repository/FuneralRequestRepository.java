package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.funeralagency.model.FuneralRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuneralRequestRepository extends JpaRepository<FuneralRequest, Long>,
        JpaSpecificationExecutor<FuneralRequest> {
    List<FuneralRequest> findAllByOrderByIdAsc();

    Optional<FuneralRequest> findByUuid(UUID uuid);

    boolean existsByClientId(long clientId);
}
