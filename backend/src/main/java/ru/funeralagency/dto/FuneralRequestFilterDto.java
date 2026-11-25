package ru.funeralagency.dto;

import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;

public class FuneralRequestFilterDto {
    private String q;
    private Long client;
    private Long ceremonyTypeId;
    private RequestStatus status;
    private Boolean urgent;
    private Integer priority;
    private boolean archived;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public Long getClient() {
        return client;
    }

    public void setClient(Long client) {
        this.client = client;
    }

    public Long getCeremonyTypeId() {
        return ceremonyTypeId;
    }

    public void setCeremonyTypeId(Long ceremonyTypeId) {
        this.ceremonyTypeId = ceremonyTypeId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }
}
