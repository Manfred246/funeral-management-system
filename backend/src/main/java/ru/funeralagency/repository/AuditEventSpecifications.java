package ru.funeralagency.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.funeralagency.model.AuditEvent;

import java.util.Locale;

public final class AuditEventSpecifications {
    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> search(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String text = search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            String pattern = "%" + text + "%";
            return cb.or(cb.like(cb.lower(root.get("eventType")), pattern, '!'),
                    cb.like(cb.lower(root.get("entityType")), pattern, '!'),
                    cb.like(cb.lower(root.get("description")), pattern, '!'),
                    cb.like(cb.lower(root.get("correlationId")), pattern, '!'));
        };
    }
}
