package ru.funeralagency.model;

public enum AuditEventType {
    LOGIN("Успешный вход"),
    REQUEST_CREATED("Заявка создана"),
    REQUEST_UPDATED("Заявка изменена"),
    REQUEST_ARCHIVED("Заявка архивирована"),
    REQUEST_RESTORED("Заявка восстановлена"),
    REQUEST_DELETED("Заявка удалена окончательно"),
    USER_CREATED("Пользователь создан"),
    PASSWORD_RESET("Установлен временный пароль");

    private final String description;

    AuditEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
