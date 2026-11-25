package ru.funeralagency.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditEventDto(Long id, String eventType, String entityType, UUID entityUuid, Long userId,
                            LocalDateTime timestamp, String description, String correlationId) {
}
