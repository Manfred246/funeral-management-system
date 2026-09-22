package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import ru.funeralagency.exception.FuneralRequestNotFoundException;
import ru.funeralagency.exception.InvalidFuneralRequestException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.validation.FuneralRequestValidationRules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
            throw new InvalidFuneralRequestException("Заявка не может быть null");
        }
        request.setId(null);
        request.setStatus(RequestStatus.NEW);
        request.setCreatedAt(LocalDateTime.now());
        normalizeAndValidate(request);
        ensureClientExists(request.getClientId());
        return requestRepository.save(request);
    }

    public List<FuneralRequest> findAll() {
        return requestRepository.findAll();
    }

    public FuneralRequest findById(Long id) {
        validateId(id);
        return requestRepository.findById(id)
                .orElseThrow(() -> new FuneralRequestNotFoundException(id));
    }

    public FuneralRequest update(Long id, FuneralRequest request) {
        validateId(id);
        if (request == null) {
            throw new InvalidFuneralRequestException("Заявка не может быть null");
        }

        FuneralRequest existing = findById(id);
        FuneralRequestValidationRules.validateStatusTransition(existing.getStatus(), request.getStatus());
        request.setId(id);
        request.setCreatedAt(existing.getCreatedAt());
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validateForUpdate(request, existing.getCeremonyDate());
        ensureClientExists(request.getClientId());
        requestRepository.update(request);
        return request;
    }

    public void deleteById(Long id) {
        findById(id);
        requestRepository.deleteById(id);
    }

    public void delete(Long id) {
        deleteById(id);
    }

    public List<FuneralRequest> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return findAll().stream()
                .filter(request -> containsIgnoreCase(request.getDeceasedFullName(), normalized)
                        || containsIgnoreCase(request.getComment(), normalized))
                .toList();
    }

    public List<FuneralRequest> filter(
            RequestStatus status,
            CeremonyType ceremonyType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new InvalidFuneralRequestException("Начальная дата не может быть позже конечной");
        }
        return findAll().stream()
                .filter(request -> status == null || request.getStatus() == status)
                .filter(request -> ceremonyType == null || request.getCeremonyType() == ceremonyType)
                .filter(request -> dateFrom == null || !request.getCeremonyDate().isBefore(dateFrom))
                .filter(request -> dateTo == null || !request.getCeremonyDate().isAfter(dateTo))
                .toList();
    }

    public List<FuneralRequest> sort(String field, boolean ascending) {
        Comparator<FuneralRequest> comparator = comparatorFor(field);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return findAll().stream().sorted(comparator).toList();
    }

    private Comparator<FuneralRequest> comparatorFor(String field) {
        if (field == null || field.isBlank() || "id".equals(field)) {
            return Comparator.comparing(FuneralRequest::getId);
        }
        return switch (field) {
            case "clientId" -> Comparator.comparing(FuneralRequest::getClientId);
            case "deceasedFullName" -> Comparator.comparing(
                    FuneralRequest::getDeceasedFullName,
                    String.CASE_INSENSITIVE_ORDER
            );
            case "ceremonyDate" -> Comparator.comparing(FuneralRequest::getCeremonyDate);
            case "ceremonyType" -> Comparator.comparing(FuneralRequest::getCeremonyType);
            case "status" -> Comparator.comparing(FuneralRequest::getStatus);
            case "price" -> Comparator.comparing(FuneralRequest::getPrice);
            case "createdAt" -> Comparator.comparing(FuneralRequest::getCreatedAt);
            default -> throw new InvalidFuneralRequestException("Неизвестное поле сортировки: " + field);
        };
    }

    private void normalizeAndValidate(FuneralRequest request) {
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validate(request);
    }

    private void ensureClientExists(Long clientId) {
        if (clientRepository.findById(clientId).isEmpty()) {
            throw new InvalidFuneralRequestException("Нельзя создать заявку для несуществующего клиента");
        }
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidFuneralRequestException("ID заявки должен быть положительным числом");
        }
    }
}
