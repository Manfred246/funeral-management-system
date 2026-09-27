package ru.funeralagency.model;

/**
 * Клиент ритуального агентства.
 *
 * <p>Данные о религии, выбранных услугах и дате церемонии относятся к заявке,
 * а не к клиенту, поэтому здесь хранятся только контактные данные.</p>
 */
public class Client {

    private Long id;
    private String fullName;
    private String phone;
    private String email;

    public Client() {
    }

    public Client(Long id, String fullName, String phone, String email) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    @Override
    public String toString() {
        return "Client{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
