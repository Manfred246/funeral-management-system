package ru.funeralagency.dto;

import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsSummaryDto(
        long totalRequests,
        long activeRequests,
        long archivedRequests,
        long urgentRequests,
        BigDecimal averagePrice,
        List<CeremonyTypeCount> byCeremonyType,
        List<StatusCount> byStatus,
        List<PriorityCount> byPriority
) {
    public record CeremonyTypeCount(Long ceremonyTypeId, String code, String name, long count) {
    }

    public record StatusCount(RequestStatus status, long count) {
    }

    public record PriorityCount(Integer priority, long count) {
    }
}
