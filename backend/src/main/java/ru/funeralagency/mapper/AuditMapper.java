package ru.funeralagency.mapper;

import ru.funeralagency.dto.AuditEventDto;
import ru.funeralagency.model.AuditEvent;

public final class AuditMapper {
    private AuditMapper() {
    }

    public static AuditEventDto toDto(AuditEvent event) {
        return new AuditEventDto(event.getId(), event.getEventType(), event.getEntityType(), event.getEntityUuid(),
                event.getUserId(), event.getTimestamp(), event.getDescription(), event.getCorrelationId());
    }
}
