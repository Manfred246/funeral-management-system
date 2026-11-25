package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.time.LocalDate;
import java.util.List;

public interface FuneralRequestRepository extends JpaRepository<FuneralRequest, Long> {
    List<FuneralRequest> findAllByOrderByIdAsc();

    boolean existsByClientId(long clientId);

    List<FuneralRequest> findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc(String name);

    List<FuneralRequest> findByClientIdOrderByIdAsc(long clientId);

    @Query("""
            select r from FuneralRequest r
            where (:status is null or r.status = :status)
              and (:type is null or r.ceremonyType = :type)
              and (cast(:dateFrom as date) is null or r.ceremonyDate >= :dateFrom)
              and (cast(:dateTo as date) is null or r.ceremonyDate <= :dateTo)
            order by r.id
            """)
    List<FuneralRequest> findFiltered(@Param("status") RequestStatus status,
                                      @Param("type") CeremonyType type,
                                      @Param("dateFrom") LocalDate dateFrom,
                                      @Param("dateTo") LocalDate dateTo);
}
