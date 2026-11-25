package ru.funeralagency.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.validation.FuneralRequestValidationRules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public FuneralRequest create(FuneralRequest request, Long clientId) {
        if (request == null) {
            throw new ValidationException("Заявка не может быть null");
        }
        request.setId(null);
        request.setStatus(RequestStatus.NEW);
        request.setClient(findClient(clientId));
        normalizeAndValidate(request);
        request.setPrice(request.getPrice().setScale(2));
        // PostgreSQL TIMESTAMP хранит микросекунды; ответ совпадает с последующим чтением БД.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setUuid(UUID.randomUUID());
        request.setRequestCode(null);
        request.setPriority(calculatePriority(request.isUrgent(), now, request.getCeremonyDate()));
        request.setArchived(false);
        request.setVersion(null);
        return requestRepository.saveAndFlush(request);
    }

    public List<FuneralRequest> findAll() {
        return requestRepository.findByArchivedFalseOrderByIdAsc();
    }

    public FuneralRequest getByUuid(UUID uuid) {
        if (uuid == null) {
            throw new ValidationException("UUID заявки обязателен");
        }
        return requestRepository.findByUuid(uuid)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Заявка с UUID " + uuid + " не найдена"
                ));
    }

    @Transactional
    public FuneralRequest updateByUuid(UUID uuid, FuneralRequest request, Long clientId, Boolean urgent, Long version) {
        if (request == null) {
            throw new ValidationException("Заявка не может быть null");
        }

        FuneralRequest existing = getByUuid(uuid);
        if (version == null || version < 0) {
            throw new ValidationException("Укажите неотрицательную версию заявки");
        }
        if (!version.equals(existing.getVersion())) {
            throw new OptimisticLockingFailureException("Версия заявки устарела");
        }
        FuneralRequestValidationRules.validateStatusTransition(existing.getStatus(), request.getStatus());
        request.setClient(findClient(clientId));
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validateForUpdate(request, existing.getCeremonyDate());
        existing.setClient(request.getClient());
        existing.setDeceasedFullName(request.getDeceasedFullName());
        existing.setCeremonyDate(request.getCeremonyDate());
        existing.setCeremonyType(request.getCeremonyType());
        existing.setStatus(request.getStatus());
        existing.setPrice(request.getPrice().setScale(2));
        existing.setComment(request.getComment());
        // Отсутствующее поле urgent сохраняет прежнее значение.
        if (urgent != null) {
            existing.setUrgent(urgent);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        existing.setPriority(calculatePriority(existing.isUrgent(), existing.getCreatedAt(), existing.getCeremonyDate()));
        existing.setUpdatedAt(now);
        return requestRepository.saveAndFlush(existing);
    }

    static int calculatePriority(boolean urgent, LocalDateTime createdAt, LocalDate ceremonyDate) {
        int priority = urgent ? 2 : 0;
        long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), ceremonyDate);
        if (days >= 0 && days <= 3) {
            priority += 2;
        } else if (days >= 4 && days <= 7) {
            priority += 1;
        }
        return priority;
    }

    @Transactional
    public void archiveByUuid(UUID uuid) {
        FuneralRequest request = getByUuid(uuid);
        if (!request.isArchived()) {
            request.setArchived(true);
            request.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            requestRepository.saveAndFlush(request);
        }
    }

    @Transactional
    public FuneralRequest restoreByUuid(UUID uuid) {
        FuneralRequest request = getByUuid(uuid);
        if (request.isArchived()) {
            request.setArchived(false);
            request.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            return requestRepository.saveAndFlush(request);
        }
        return request;
    }

    @Transactional
    public void deletePermanentlyByUuid(UUID uuid) {
        FuneralRequest request = getByUuid(uuid);
        requestRepository.delete(request);
        requestRepository.flush();
    }

    public List<FuneralRequest> search(String deceasedName, Long clientId) {
        if ((deceasedName == null && clientId == null) || (deceasedName != null && clientId != null)) {
            throw new ValidationException("Укажите один параметр поиска: deceasedName или clientId");
        }
        if (clientId != null) {
            if (clientId <= 0) {
                throw new ValidationException("ID клиента должен быть положительным числом");
            }
            return requestRepository.findByClientIdOrderByIdAsc(clientId);
        }
        if (deceasedName.isBlank()) {
            throw new ValidationException("ФИО для поиска не может быть пустым");
        }
        return requestRepository.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc(deceasedName.trim());
    }

    public List<FuneralRequest> filter(
            RequestStatus status,
            CeremonyType ceremonyType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ValidationException("Начальная дата не может быть позже конечной");
        }
        return requestRepository.findFiltered(status, ceremonyType, dateFrom, dateTo);
    }

    public List<FuneralRequest> sort(String field, boolean ascending) {
        if (!"ceremonyDate".equals(field) && !"price".equals(field)) {
            throw new ValidationException("Сортировать можно по ceremonyDate или price");
        }
        Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        return requestRepository.findAll(Sort.by(direction, field).and(Sort.by("id")));
    }

    private void normalizeAndValidate(FuneralRequest request) {
        FuneralRequestValidationRules.normalize(request);
        FuneralRequestValidationRules.validate(request);
    }

    private Client findClient(Long clientId) {
        if (clientId == null || clientId <= 0) {
            throw new ValidationException("ID клиента должен быть положительным числом");
        }
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ValidationException("Нельзя создать заявку для несуществующего клиента"));
    }
}
