package ru.funeralagency.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "funeral_requests")
public class FuneralRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "deceased_full_name", nullable = false, length = 255)
    private String deceasedFullName;

    @Column(name = "ceremony_date", nullable = false)
    private LocalDate ceremonyDate;

    @Convert(converter = CeremonyTypeConverter.class)
    @Column(name = "ceremony_type_id", nullable = false)
    private CeremonyType ceremonyType;

    @Convert(converter = RequestStatusConverter.class)
    @Column(name = "status_id", nullable = false)
    private RequestStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // Значение по-прежнему назначает PostgreSQL; Hibernate читает его после INSERT.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "text")
    private String comment;

    public FuneralRequest() {
    }

    public FuneralRequest(
            Long id,
            Client client,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyType ceremonyType,
            RequestStatus status,
            BigDecimal price,
            LocalDateTime createdAt,
            String comment
    ) {
        this.id = id;
        this.client = client;
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

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
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
    public String toString() {
        return "FuneralRequest{" +
                "id=" + id +
                ", clientId=" + (client == null ? null : client.getId()) +
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
