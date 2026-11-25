package ru.funeralagency.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CeremonyTypeConverter implements AttributeConverter<CeremonyType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(CeremonyType value) {
        return value == null ? null : value.getDatabaseId();
    }

    @Override
    public CeremonyType convertToEntityAttribute(Integer id) {
        return id == null ? null : CeremonyType.fromDatabaseId(id);
    }
}
