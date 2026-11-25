package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FuneralRequestUpdateDto {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long clientId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String deceasedFullName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate ceremonyDate;

    @NotNull(message = "Тип церемонии обязателен")
    @Positive(message = "ID типа церемонии должен быть положительным")
    private Long ceremonyTypeId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private RequestStatus status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;
    private String comment;
    private Boolean urgent;

    @NotNull(message = "Версия заявки обязательна")
    @PositiveOrZero(message = "Версия заявки не может быть отрицательной")
    private Long version;

    public FuneralRequestUpdateDto() {
    }

    public FuneralRequestUpdateDto(
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            Long ceremonyTypeId,
            RequestStatus status,
            BigDecimal price,
            String comment
    ) {
        this.clientId = clientId;
        this.deceasedFullName = deceasedFullName;
        this.ceremonyDate = ceremonyDate;
        this.ceremonyTypeId = ceremonyTypeId;
        this.status = status;
        this.price = price;
        this.comment = comment;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
