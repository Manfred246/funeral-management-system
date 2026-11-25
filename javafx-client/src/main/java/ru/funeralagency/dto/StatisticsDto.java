package ru.funeralagency.dto;

import java.math.BigDecimal;

public class StatisticsDto {
    private long totalClients;
    private long totalRequests;
    private long newRequests;
    private long completedRequests;
    private long cancelledRequests;
    private BigDecimal averagePrice;

    public StatisticsDto() {
    }

    public long getTotalClients() {
        return totalClients;
    }

    public void setTotalClients(long totalClients) {
        this.totalClients = totalClients;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getNewRequests() {
        return newRequests;
    }

    public void setNewRequests(long newRequests) {
        this.newRequests = newRequests;
    }

    public long getCompletedRequests() {
        return completedRequests;
    }

    public void setCompletedRequests(long completedRequests) {
        this.completedRequests = completedRequests;
    }

    public long getCancelledRequests() {
        return cancelledRequests;
    }

    public void setCancelledRequests(long cancelledRequests) {
        this.cancelledRequests = cancelledRequests;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }
}
