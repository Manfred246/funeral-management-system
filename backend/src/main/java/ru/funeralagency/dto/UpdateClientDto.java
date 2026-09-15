package ru.funeralagency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Данные для полного изменения клиента. */
public class UpdateClientDto {

    @NotBlank(message = "ФИО клиента обязательно")
    @Size(max = 255, message = "ФИО клиента не должно превышать 255 символов")
    private String fullName;

    @NotBlank(message = "Телефон клиента обязателен")
    @Size(max = 32, message = "Телефон клиента не должен превышать 32 символа")
    private String phone;

    @Email(message = "Некорректный email")
    @Size(max = 255, message = "Email не должен превышать 255 символов")
    private String email;

    public UpdateClientDto() {
    }

    public UpdateClientDto(String fullName, String phone, String email) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
