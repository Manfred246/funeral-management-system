package ru.funeralagency.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RequestStatusConverter implements AttributeConverter<RequestStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(RequestStatus value) {
        return value == null ? null : value.getDatabaseId();
    }

    @Override
    public RequestStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RequestStatus.fromDatabaseId(id);
    }
}
