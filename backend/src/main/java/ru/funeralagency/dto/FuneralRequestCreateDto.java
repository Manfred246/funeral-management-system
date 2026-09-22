package ru.funeralagency.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.funeralagency.model.CeremonyType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Данные для создания заявки; ID и время создания назначаются сервером. */
public class FuneralRequestCreateDto {

    @NotNull(message = "ID клиента обязателен")
    @Positive(message = "ID клиента должен быть положительным")
    private Long clientId;

    @NotBlank(message = "ФИО умершего обязательно")
    @Size(max = 150, message = "ФИО умершего не должно превышать 150 символов")
    private String deceasedFullName;

    @NotNull(message = "Дата церемонии обязательна")
    @FutureOrPresent(message = "Дата церемонии не может находиться в прошлом")
    private LocalDate ceremonyDate;

    @NotNull(message = "Тип церемонии обязателен")
    private CeremonyType ceremonyType;

    @NotNull(message = "Стоимость обязательна")
    @DecimalMin(value = "0.00", message = "Стоимость не может быть отрицательной")
    private BigDecimal price;

    @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов")
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
