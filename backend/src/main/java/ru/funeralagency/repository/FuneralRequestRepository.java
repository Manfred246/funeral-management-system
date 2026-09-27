package ru.funeralagency.repository;

import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FuneralRequestRepository {
    FuneralRequest create(FuneralRequest request);

    List<FuneralRequest> findAll();

    Optional<FuneralRequest> findById(long id);

    FuneralRequest update(FuneralRequest request);

    void deleteById(long id);

    boolean existsByClientId(long clientId);

    List<FuneralRequest> findByDeceasedName(String name);

    List<FuneralRequest> findByClientId(long clientId);

    List<FuneralRequest> findByStatus(RequestStatus status);

    List<FuneralRequest> findByCeremonyType(CeremonyType type);

    List<FuneralRequest> findFiltered(RequestStatus status, CeremonyType type,
                                      LocalDate dateFrom, LocalDate dateTo);

    List<FuneralRequest> findAllOrderByCeremonyDate(boolean ascending);

    List<FuneralRequest> findAllOrderByPrice(boolean ascending);
}
