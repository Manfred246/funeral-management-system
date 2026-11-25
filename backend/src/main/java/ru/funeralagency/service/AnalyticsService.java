package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.AnalyticsSummaryDto;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyticsService {
    private final FuneralRequestRepository repository;

    public AnalyticsService(FuneralRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary() {
        var totals = repository.summarizeRequests();
        BigDecimal averagePrice = BigDecimal.ZERO.setScale(2);
        if (totals.getTotalRequests() > 0) {
            // SUM(NUMERIC) сохраняет точность цены; JPQL AVG вернул бы Double.
            averagePrice = totals.getTotalPrice().divide(BigDecimal.valueOf(totals.getTotalRequests()), 2, RoundingMode.HALF_UP);
        }
        List<AnalyticsSummaryDto.CeremonyTypeCount> byCeremonyType = new ArrayList<>();
        for (var group : repository.countByCeremonyType()) {
            byCeremonyType.add(new AnalyticsSummaryDto.CeremonyTypeCount(
                    group.getCeremonyTypeId(), group.getCode(), group.getName(), group.getRequestCount()));
        }
        List<AnalyticsSummaryDto.StatusCount> byStatus = new ArrayList<>();
        for (var group : repository.countByStatus()) {
            byStatus.add(new AnalyticsSummaryDto.StatusCount(group.getStatus(), group.getRequestCount()));
        }
        List<AnalyticsSummaryDto.PriorityCount> byPriority = new ArrayList<>();
        for (var group : repository.countByPriority()) {
            byPriority.add(new AnalyticsSummaryDto.PriorityCount(group.getPriority(), group.getRequestCount()));
        }
        return new AnalyticsSummaryDto(totals.getTotalRequests(), totals.getActiveRequests(), totals.getArchivedRequests(),
                totals.getUrgentRequests(), averagePrice, byCeremonyType, byStatus, byPriority);
    }
}
