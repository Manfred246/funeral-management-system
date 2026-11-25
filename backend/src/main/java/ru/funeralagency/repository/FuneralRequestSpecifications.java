package ru.funeralagency.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.model.FuneralRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FuneralRequestSpecifications {
    private FuneralRequestSpecifications() {
    }

    public static Specification<FuneralRequest> matching(FuneralRequestFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("archived"), filter.isArchived()));
            if (filter.getTag() != null) {
                predicates.add(cb.equal(root.join("tags").get("id"), filter.getTag()));
                query.distinct(true);
            }
            if (filter.getQ() != null && !filter.getQ().isBlank()) {
                String pattern = containsPattern(filter.getQ());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("deceasedFullName")), pattern, '!'),
                        cb.like(cb.lower(root.get("requestCode")), pattern, '!'),
                        cb.like(cb.lower(root.get("client").get("fullName")), pattern, '!')));
            }
            if (filter.getClient() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), filter.getClient()));
            }
            if (filter.getCeremonyTypeId() != null) {
                predicates.add(cb.equal(root.get("ceremonyType").get("id"), filter.getCeremonyTypeId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getUrgent() != null) {
                predicates.add(cb.equal(root.get("urgent"), filter.getUrgent()));
            }
            if (filter.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.getPriority()));
            }
            if (filter.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
            }
            if (filter.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Legacy-поиск сохраняет прежнее значение deceasedName: только ФИО умершего.
    public static Specification<FuneralRequest> deceasedNameContains(String name) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("deceasedFullName")), containsPattern(name), '!');
    }

    public static Specification<FuneralRequest> ceremonyDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ceremonyDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("ceremonyDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String containsPattern(String value) {
        // Escape-символ экранируется первым, чтобы %, _ и ! искались буквально.
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
