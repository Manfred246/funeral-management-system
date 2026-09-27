package ru.funeralagency.dto;

/** Данные клиента, принимаемые REST API при создании и изменении. */
public class ClientRequestDto {

    private String fullName;
    private String phone;
    private String email;

    public ClientRequestDto() {
    }

    public ClientRequestDto(String fullName, String phone, String email) {
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
