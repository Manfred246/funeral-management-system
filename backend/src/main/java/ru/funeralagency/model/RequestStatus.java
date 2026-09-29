package ru.funeralagency.model;

/** Этап обработки заявки на ритуальные услуги. */
public enum RequestStatus {
    NEW,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
