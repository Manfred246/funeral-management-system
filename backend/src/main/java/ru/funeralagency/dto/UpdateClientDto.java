package ru.funeralagency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.funeralagency.validation.ClientValidationRules;

/** Данные для полного изменения клиента. */
public class UpdateClientDto {

    @NotBlank(message = "ФИО клиента обязательно")
    @Size(
            max = ClientValidationRules.MAX_FULL_NAME_LENGTH,
            message = "ФИО клиента не должно превышать 100 символов"
    )
    @Pattern(
            regexp = ClientValidationRules.FULL_NAME_REGEXP,
            message = "ФИО должно содержать от двух до трёх слов и только буквы, дефис или апостроф"
    )
    private String fullName;

    @NotBlank(message = "Телефон клиента обязателен")
    @Size(
            max = ClientValidationRules.MAX_PHONE_LENGTH,
            message = "Телефон клиента не должен превышать 25 символов"
    )
    @Pattern(
            regexp = ClientValidationRules.PHONE_REGEXP,
            message = "Телефон должен содержать от 10 до 15 цифр и допустимые разделители"
    )
    private String phone;

    @Email(message = "Некорректный email")
    @Size(
            max = ClientValidationRules.MAX_EMAIL_LENGTH,
            message = "Email не должен превышать 254 символа"
    )
    @Pattern(regexp = ClientValidationRules.EMAIL_REGEXP, message = "Некорректный email")
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
