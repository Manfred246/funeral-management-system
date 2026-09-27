package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.validation.FuneralRequestValidationRules;

import java.time.LocalDate;
import java.util.List;

/** Бизнес-логика работы с заявками. */
@Service
public class FuneralRequestService {

    private final FuneralRequestRepository requestRepository;
    private final ClientRepository clientRepository;

    public FuneralRequestService(
            FuneralRequestRepository requestRepository,
            ClientRepository clientRepository
    ) {
        this.requestRepository = requestRepository;
        this.clientRepository = clientRepository;
    }

    public FuneralRequest create(FuneralRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявка не может быть null");
        }
        request.setId(null);
        request.setStatus(RequestStatus.NEW);
        request.setCreatedAt(null);
        normalizeAndValidate(request);
        ensureClientExists(request.getClientId());
        return requestRepository.create(request);
    }

    public List<FuneralRequest> findAll() {
        return requestRepository.findAll();
    }

    public FuneralRequest findById(Long id) {
        validateId(id);
        return requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Заявка с ID " + id + " не найдена"));
    }

    public FuneralRequest update(Long id, FuneralRequest request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("Заявка не может быть null");
        }

        FuneralRequest existing = findById(id);
        FuneralRequestValidationRules.validateStatusTransition(existing.getStatus(), request.getStatus());
        request.setId(id);
        request.setCreatedAt(existing.getCreatedAt());
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validateForUpdate(request, existing.getCeremonyDate());
        ensureClientExists(request.getClientId());
        return requestRepository.update(request);
    }

    public void deleteById(Long id) {
        findById(id);
        requestRepository.deleteById(id);
    }

    public List<FuneralRequest> search(String deceasedName, Long clientId) {
        if ((deceasedName == null && clientId == null) || (deceasedName != null && clientId != null)) {
            throw new IllegalArgumentException("Укажите один параметр поиска: deceasedName или clientId");
        }
        if (clientId != null) {
            if (clientId <= 0) {
                throw new IllegalArgumentException("ID клиента должен быть положительным числом");
            }
            return requestRepository.findByClientId(clientId);
        }
        if (deceasedName.isBlank()) {
            throw new IllegalArgumentException("ФИО для поиска не может быть пустым");
        }
        return requestRepository.findByDeceasedName(deceasedName.trim());
    }

    public List<FuneralRequest> filter(
            RequestStatus status,
            CeremonyType ceremonyType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Начальная дата не может быть позже конечной");
        }
        return requestRepository.findFiltered(status, ceremonyType, dateFrom, dateTo);
    }

    public List<FuneralRequest> sort(String field, boolean ascending) {
        if ("ceremonyDate".equals(field)) {
            return requestRepository.findAllOrderByCeremonyDate(ascending);
        }
        if ("price".equals(field)) {
            return requestRepository.findAllOrderByPrice(ascending);
        }
        throw new IllegalArgumentException("Сортировать можно по ceremonyDate или price");
    }

    private void normalizeAndValidate(FuneralRequest request) {
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validate(request);
    }

    private void ensureClientExists(Long clientId) {
        if (clientRepository.findById(clientId).isEmpty()) {
            throw new IllegalArgumentException("Нельзя создать заявку для несуществующего клиента");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID заявки должен быть положительным числом");
        }
    }
}
