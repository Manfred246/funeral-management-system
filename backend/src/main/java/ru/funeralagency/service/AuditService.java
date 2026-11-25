package ru.funeralagency.service;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.AuditPageDto;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.filter.CorrelationIdFilter;
import ru.funeralagency.mapper.AuditMapper;
import ru.funeralagency.model.AuditEvent;
import ru.funeralagency.model.AuditEventType;
import ru.funeralagency.repository.AuditEventRepository;
import ru.funeralagency.repository.AuditEventSpecifications;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.AuthenticatedUser;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final UserRepository users;

    public AuditService(AuditEventRepository events, UserRepository users) {
        this.events = events;
        this.users = users;
    }

    @Transactional
    public void record(AuditEventType type, String entityType, UUID entityUuid) {
        Long actorId = null;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            actorId = users.findByUuid(principal.uuid()).map(user -> user.getId()).orElse(null);
        }
        record(type, entityType, entityUuid, actorId);
    }

    @Transactional
    public void record(AuditEventType type, String entityType, UUID entityUuid, Long actorId) {
        String correlationId = MDC.get(CorrelationIdFilter.ATTRIBUTE);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        events.save(new AuditEvent(type.name(), entityType, entityUuid, actorId,
                LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), type.getDescription(), correlationId));
    }

    @Transactional(readOnly = true)
    public AuditPageDto findPage(String search, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ValidationException("Укажите page >= 0 и size от 1 до 100 без переполнения номера страницы");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("timestamp", "eventType", "entityType").contains(parts[0])
                || (!"asc".equalsIgnoreCase(parts[1]) && !"desc".equalsIgnoreCase(parts[1]))) {
            throw new ValidationException("sort: timestamp/eventType/entityType и направление asc/desc");
        }
        Sort order = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]).and(Sort.by(Sort.Direction.DESC, "id"));
        var result = events.findAll(AuditEventSpecifications.search(search), PageRequest.of(page, size, order));
        return new AuditPageDto(result.map(AuditMapper::toDto).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
