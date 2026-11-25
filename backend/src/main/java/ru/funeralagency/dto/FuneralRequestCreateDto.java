package ru.funeralagency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FuneralRequestCreateDto {

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
    private BigDecimal price;
    private String comment;
    private boolean urgent;

    public FuneralRequestCreateDto() {
    }

    public FuneralRequestCreateDto(
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            Long ceremonyTypeId,
            BigDecimal price,
            String comment
    ) {
        this.clientId = clientId;
        this.deceasedFullName = deceasedFullName;
        this.ceremonyDate = ceremonyDate;
        this.ceremonyTypeId = ceremonyTypeId;
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

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }
}
