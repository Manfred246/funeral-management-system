package ru.funeralagency.dto;

import ru.funeralagency.model.CeremonyType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Данные для создания заявки; ID и время создания назначаются сервером. */
public class FuneralRequestCreateDto {

    private Long clientId;
    private String deceasedFullName;
    private LocalDate ceremonyDate;
    private CeremonyType ceremonyType;
    private BigDecimal price;
    private String comment;

    public FuneralRequestCreateDto() {
    }

    public FuneralRequestCreateDto(
            Long clientId,
            String deceasedFullName,
            LocalDate ceremonyDate,
            CeremonyType ceremonyType,
            BigDecimal price,
            String comment
    ) {
        this.clientId = clientId;
        this.deceasedFullName = deceasedFullName;
        this.ceremonyDate = ceremonyDate;
        this.ceremonyType = ceremonyType;
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

    public CeremonyType getCeremonyType() {
        return ceremonyType;
    }

    public void setCeremonyType(CeremonyType ceremonyType) {
        this.ceremonyType = ceremonyType;
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
}
