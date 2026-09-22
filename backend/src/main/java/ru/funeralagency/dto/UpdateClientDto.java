package ru.funeralagency.dto;

/** Данные для полного изменения клиента. */
public class UpdateClientDto {

    private String fullName;
    private String phone;
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
