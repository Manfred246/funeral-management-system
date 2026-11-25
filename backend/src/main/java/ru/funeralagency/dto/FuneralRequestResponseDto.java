package ru.funeralagency.dto;

import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class FuneralRequestResponseDto {

    private Long id;
    private Long clientId;
    private String deceasedFullName;
    private LocalDate ceremonyDate;
    private CeremonyTypeResponseDto ceremonyType;
    private RequestStatus status;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private String comment;
    private UUID uuid;
    private String requestCode;
    private boolean urgent;
    private Integer priority;
    private boolean archived;
    private LocalDateTime updatedAt;
    private Long version;
    private List<TagDto> tags = new ArrayList<>();

    public FuneralRequestResponseDto() {
    }

    public FuneralRequestResponseDto(
            Long id,
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyTypeResponseDto ceremonyType,
            RequestStatus status,
            BigDecimal price,
            LocalDateTime createdAt,
            String comment
    ) {
        this.id = id;
        this.clientId = clientId;
        this.deceasedFullName = deceasedFullName;
        this.ceremonyDate = ceremonyDate;
        this.ceremonyType = ceremonyType;
        this.status = status;
        this.price = price;
        this.createdAt = createdAt;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getDeceasedFullName() {
        return deceasedFullName;
    }

    public void setDeceasedFullName(String deceasedFullName) {
        this.deceasedFullName = deceasedFullName;
    }

    public LocalDate getCeremonyDate() {
        return ceremonyDate;
    }

    public void setCeremonyDate(LocalDate ceremonyDate) {
        this.ceremonyDate = ceremonyDate;
    }

    public CeremonyTypeResponseDto getCeremonyType() {
        return ceremonyType;
    }

    public void setCeremonyType(CeremonyTypeResponseDto ceremonyType) {
        this.ceremonyType = ceremonyType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<TagDto> getTags() {
        return tags;
    }

    public void setTags(List<TagDto> tags) {
        this.tags = tags;
    }
}
