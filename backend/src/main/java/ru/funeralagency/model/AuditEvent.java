package ru.funeralagency.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    @Column(name = "entity_uuid", nullable = false)
    private UUID entityUuid;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime timestamp;
    @Column(nullable = false, length = 255)
    private String description;
    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    public AuditEvent() {
    }

    public AuditEvent(String eventType, String entityType, UUID entityUuid, Long userId,
                      LocalDateTime timestamp, String description, String correlationId) {
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityUuid = entityUuid;
        this.userId = userId;
        this.timestamp = timestamp;
        this.description = description;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public UUID getEntityUuid() { return entityUuid; }
    public Long getUserId() { return userId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getDescription() { return description; }
    public String getCorrelationId() { return correlationId; }
}
