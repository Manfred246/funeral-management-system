package ru.funeralagency.mapper;

import ru.funeralagency.dto.CeremonyTypeResponseDto;
import ru.funeralagency.model.CeremonyType;

import java.util.ArrayList;
import java.util.List;

public final class CeremonyTypeMapper {
    private CeremonyTypeMapper() {
    }

    public static CeremonyTypeResponseDto toResponseDto(CeremonyType type) {
        if (type == null) {
            return null;
        }
        return new CeremonyTypeResponseDto(type.getId(), type.getCode(), type.getName(), type.isEnabled());
    }

    public static List<CeremonyTypeResponseDto> toResponseDtoList(List<CeremonyType> types) {
        List<CeremonyTypeResponseDto> result = new ArrayList<>();
        for (CeremonyType type : types) {
            result.add(toResponseDto(type));
        }
        return result;
    }
}
