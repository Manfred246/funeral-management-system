package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.funeralagency.model.FuneralRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuneralRequestRepository extends JpaRepository<FuneralRequest, Long>,
        JpaSpecificationExecutor<FuneralRequest> {
    List<FuneralRequest> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"client", "ceremonyType", "tags"})
    Optional<FuneralRequest> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"client", "ceremonyType"})
    Optional<FuneralRequest> findByRequestCode(String requestCode);

    // Collection fetch join применяется только к непагинируемым legacy-спискам.
    @Override
    @EntityGraph(attributePaths = {"client", "ceremonyType", "tags"})
    List<FuneralRequest> findAll(Specification<FuneralRequest> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"client", "ceremonyType"})
    Page<FuneralRequest> findAll(Specification<FuneralRequest> spec, Pageable pageable);

    boolean existsByClientId(long clientId);
}
