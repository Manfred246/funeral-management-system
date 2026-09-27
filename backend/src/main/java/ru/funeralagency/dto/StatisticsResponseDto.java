package ru.funeralagency.dto;

import java.math.BigDecimal;

/** Сводные показатели системы. */
public class StatisticsResponseDto {
    private final long totalClients;
    private final long totalRequests;
    private final long newRequests;
    private final long completedRequests;
    private final long cancelledRequests;
    private final BigDecimal averagePrice;

    public StatisticsResponseDto(
            long totalClients,
            long totalRequests,
            long newRequests,
            long completedRequests,
            long cancelledRequests,
            BigDecimal averagePrice
    ) {
        this.totalClients = totalClients;
        this.totalRequests = totalRequests;
        this.newRequests = newRequests;
        this.completedRequests = completedRequests;
        this.cancelledRequests = cancelledRequests;
        this.averagePrice = averagePrice;
    }

    public long getTotalClients() {
        return totalClients;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getNewRequests() {
        return newRequests;
    }

    public long getCompletedRequests() {
        return completedRequests;
    }

    public long getCancelledRequests() {
        return cancelledRequests;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }
}
