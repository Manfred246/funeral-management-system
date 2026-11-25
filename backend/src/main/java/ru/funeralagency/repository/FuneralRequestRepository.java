package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
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

    interface SummaryTotals {
        Long getTotalRequests();
        Long getActiveRequests();
        Long getArchivedRequests();
        Long getUrgentRequests();
        BigDecimal getTotalPrice();
    }

    interface CeremonyTypeCount {
        Long getCeremonyTypeId();
        String getCode();
        String getName();
        Long getRequestCount();
    }

    interface StatusCount {
        RequestStatus getStatus();
        Long getRequestCount();
    }

    interface PriorityCount {
        Integer getPriority();
        Long getRequestCount();
    }

    @Query("""
            select count(r) as totalRequests,
                   coalesce(sum(case when r.archived = false then 1 else 0 end), 0) as activeRequests,
                   coalesce(sum(case when r.archived = true then 1 else 0 end), 0) as archivedRequests,
                   coalesce(sum(case when r.urgent = true then 1 else 0 end), 0) as urgentRequests,
                   coalesce(sum(r.price), 0) as totalPrice
            from FuneralRequest r
            """)
    SummaryTotals summarizeRequests();

    @Query("""
            select ct.id as ceremonyTypeId, ct.code as code, ct.name as name, count(r) as requestCount
            from FuneralRequest r join r.ceremonyType ct
            group by ct.id, ct.code, ct.name
            order by ct.id
            """)
    List<CeremonyTypeCount> countByCeremonyType();

    @Query("""
            select r.status as status, count(r) as requestCount
            from FuneralRequest r
            group by r.status
            order by r.status
            """)
    List<StatusCount> countByStatus();

    @Query("""
            select r.priority as priority, count(r) as requestCount
            from FuneralRequest r
            group by r.priority
            order by r.priority
            """)
    List<PriorityCount> countByPriority();
}
