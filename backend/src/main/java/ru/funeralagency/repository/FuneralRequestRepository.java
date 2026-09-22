package ru.funeralagency.repository;

import ru.funeralagency.model.FuneralRequest;

import java.util.List;
import java.util.Optional;

/** Хранилище заявок на ритуальные услуги. */
public interface FuneralRequestRepository {

    FuneralRequest save(FuneralRequest request);

    Optional<FuneralRequest> findById(Long id);

    List<FuneralRequest> findAll();

    void update(FuneralRequest request);

    void deleteById(Long id);
}
