package ru.funeralagency.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Заявка клиента на организацию ритуальных услуг. */
public class FuneralRequest {

    private Long id;
    private Long clientId;
    private String deceasedFullName;
    private LocalDate ceremonyDate;
    private CeremonyType ceremonyType;
    private RequestStatus status;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private String comment;

    public FuneralRequest() {
    }

    public FuneralRequest(
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyType ceremonyType,
            BigDecimal price,
            String comment
    ) {
        this(
                null,
                clientId,
                deceasedFullName,
                ceremonyDate,
                ceremonyType,
                null,
                price,
                null,
                comment
        );
    }

    public FuneralRequest(
            Long id,
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyType ceremonyType,
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

    public CeremonyType getCeremonyType() {
        return ceremonyType;
    }

    public void setCeremonyType(CeremonyType ceremonyType) {
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FuneralRequest that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(clientId, that.clientId)
                && Objects.equals(deceasedFullName, that.deceasedFullName)
                && Objects.equals(ceremonyDate, that.ceremonyDate)
                && ceremonyType == that.ceremonyType
                && status == that.status
                && Objects.equals(price, that.price)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                clientId,
                deceasedFullName,
                ceremonyDate,
                ceremonyType,
                status,
                price,
                createdAt,
                comment
        );
    }

    @Override
    public String toString() {
        return "FuneralRequest{" +
                "id=" + id +
                ", clientId=" + clientId +
                ", deceasedFullName='" + deceasedFullName + '\'' +
                ", ceremonyDate=" + ceremonyDate +
                ", ceremonyType=" + ceremonyType +
                ", status=" + status +
                ", price=" + price +
                ", createdAt=" + createdAt +
                ", comment='" + comment + '\'' +
                '}';
    }
}
