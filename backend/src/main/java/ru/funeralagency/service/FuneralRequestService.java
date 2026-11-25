package ru.funeralagency.service;

import org.hibernate.Hibernate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.dto.FuneralRequestPageDto;
import ru.funeralagency.dto.TagDto;
import ru.funeralagency.mapper.TagMapper;
import ru.funeralagency.model.Tag;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.FuneralRequestSpecifications;
import ru.funeralagency.validation.FuneralRequestValidationRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FuneralRequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "ceremonyDate", "price", "priority", "requestCode");

    private final FuneralRequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final FuneralRequestMapper requestMapper;
    private final CeremonyTypeRepository ceremonyTypeRepository;
    private final TagService tagService;

    public FuneralRequestService(
            FuneralRequestRepository requestRepository,
            ClientRepository clientRepository,
            FuneralRequestMapper requestMapper,
            CeremonyTypeRepository ceremonyTypeRepository,
            TagService tagService
    ) {
        this.requestRepository = requestRepository;
        this.clientRepository = clientRepository;
        this.requestMapper = requestMapper;
        this.ceremonyTypeRepository = ceremonyTypeRepository;
        this.tagService = tagService;
    }

    @Transactional
    public FuneralRequest create(FuneralRequest request, Long clientId, Long ceremonyTypeId) {
        if (request == null) {
            throw new ValidationException("Заявка не может быть null");
        }
        request.setId(null);
        request.setStatus(RequestStatus.NEW);
        request.setClient(findClient(clientId));
        CeremonyType type = findCeremonyType(ceremonyTypeId);
        if (!type.isEnabled()) {
            throw new BusinessException("Отключённый тип церемонии нельзя назначить заявке");
        }
        request.setCeremonyType(type);
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
        return requestRepository.findAll(
                FuneralRequestSpecifications.matching(new FuneralRequestFilterDto()), Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public FuneralRequestPageDto findPage(FuneralRequestFilterDto filter, int page, int size, String sort) {
        validateFilters(filter);
        if (page < 0) {
            throw new ValidationException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidationException("Размер страницы должен быть от 1 до " + MAX_PAGE_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new ValidationException("Номер страницы слишком велик");
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Page<FuneralRequest> result = requestRepository.findAll(FuneralRequestSpecifications.matching(filter), pageable);
        // BatchSize загружает коллекции страницы в одной сессии, без collection fetch join с LIMIT/OFFSET.
        for (FuneralRequest request : result.getContent()) {
            Hibernate.initialize(request.getTags());
        }
        return new FuneralRequestPageDto(requestMapper.toResponseDtoList(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
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
    public FuneralRequest updateByUuid(UUID uuid, FuneralRequest request, Long clientId,
                                       Long ceremonyTypeId, Boolean urgent, Long version) {
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
        CeremonyType type = findCeremonyType(ceremonyTypeId);
        if (!type.isEnabled() && !type.getId().equals(existing.getCeremonyType().getId())) {
            throw new BusinessException("Отключённый тип церемонии нельзя назначить заявке");
        }
        request.setCeremonyType(type);
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

    @Transactional(readOnly = true)
    public List<TagDto> getTagsByUuid(UUID uuid) {
        return TagMapper.toDtoList(getByUuid(uuid).getTags());
    }

    @Transactional
    public void addTagByUuid(UUID uuid, Long tagId) {
        FuneralRequest request = getByUuid(uuid);
        if (request.isArchived()) {
            throw new BusinessException("Нельзя изменять теги архивной заявки");
        }
        Tag tag = tagService.getById(tagId);
        for (Tag existing : request.getTags()) {
            if (existing.getId().equals(tag.getId())) {
                return;
            }
        }
        request.getTags().add(tag);
        request.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        requestRepository.saveAndFlush(request);
    }

    @Transactional
    public void removeTagByUuid(UUID uuid, Long tagId) {
        FuneralRequest request = getByUuid(uuid);
        if (request.isArchived()) {
            throw new BusinessException("Нельзя изменять теги архивной заявки");
        }
        Tag tag = tagService.getById(tagId);
        if (request.getTags().removeIf(existing -> existing.getId().equals(tag.getId()))) {
            request.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            requestRepository.saveAndFlush(request);
        }
    }

    @Transactional
    public void changeStatusByUuid(UUID uuid, RequestStatus status) {
        FuneralRequest request = getByUuid(uuid);
        FuneralRequestValidationRules.validateStatusTransition(request.getStatus(), status);
        if (request.getStatus() != status) {
            request.setStatus(status);
            request.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            requestRepository.saveAndFlush(request);
        }
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
        if (deceasedName != null && deceasedName.isBlank()) {
            throw new ValidationException("ФИО для поиска не может быть пустым");
        }
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setClient(clientId);
        validateFilters(filter);
        Specification<FuneralRequest> spec = FuneralRequestSpecifications.matching(filter);
        if (deceasedName != null) {
            spec = spec.and(FuneralRequestSpecifications.deceasedNameContains(deceasedName));
        }
        return requestRepository.findAll(spec, Sort.by("id"));
    }

    public List<FuneralRequest> filter(
            RequestStatus status,
            String ceremonyType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ValidationException("Начальная дата не может быть позже конечной");
        }
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setStatus(status);
        if (ceremonyType != null) {
            String code = ceremonyType.trim().toUpperCase(Locale.ROOT);
            CeremonyType type = ceremonyTypeRepository.findByCode(code)
                    .orElseThrow(() -> new EntityNotFoundException("Тип церемонии с кодом " + code + " не найден"));
            filter.setCeremonyTypeId(type.getId());
        }
        Specification<FuneralRequest> spec = FuneralRequestSpecifications.matching(filter)
                .and(FuneralRequestSpecifications.ceremonyDateBetween(dateFrom, dateTo));
        return requestRepository.findAll(spec, Sort.by("id"));
    }

    public List<FuneralRequest> sort(String field, boolean ascending) {
        return requestRepository.findAll(FuneralRequestSpecifications.matching(new FuneralRequestFilterDto()),
                parseSort(field + (ascending ? ",asc" : ",desc")));
    }

    private void validateFilters(FuneralRequestFilterDto filter) {
        if (filter.getTag() != null && filter.getTag() <= 0) {
            throw new ValidationException("ID тега должен быть положительным числом");
        }
        Long typeId = filter.getCeremonyTypeId();
        if (typeId != null && (typeId <= 0 || typeId > Integer.MAX_VALUE)) {
            throw new ValidationException("ID типа церемонии должен быть от 1 до " + Integer.MAX_VALUE);
        }
        if (filter.getClient() != null && filter.getClient() <= 0) {
            throw new ValidationException("ID клиента должен быть положительным числом");
        }
        if (filter.getPriority() != null && filter.getPriority() < 0) {
            throw new ValidationException("Priority не может быть отрицательным");
        }
        BigDecimal min = filter.getMinPrice();
        BigDecimal max = filter.getMaxPrice();
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
            throw new ValidationException("Границы стоимости не могут быть отрицательными");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ValidationException("minPrice не может превышать maxPrice");
        }
    }

    private Sort parseSort(String sort) {
        String value = sort == null ? "createdAt,desc" : sort;
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0].trim())) {
            throw new ValidationException("Допустимые поля sort: createdAt, ceremonyDate, price, priority, requestCode");
        }
        String direction = parts[1].trim();
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new ValidationException("Направление sort должно быть asc или desc");
        }
        // Внутренний ID задаёт устойчивый порядок при одинаковых значениях основного поля.
        return Sort.by(Sort.Direction.fromString(direction), parts[0].trim()).and(Sort.by("id"));
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

    private CeremonyType findCeremonyType(Long id) {
        if (id == null || id <= 0 || id > Integer.MAX_VALUE) {
            throw new ValidationException("ID типа церемонии должен быть от 1 до " + Integer.MAX_VALUE);
        }
        return ceremonyTypeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(
                "Тип церемонии с ID " + id + " не найден"));
    }
}
