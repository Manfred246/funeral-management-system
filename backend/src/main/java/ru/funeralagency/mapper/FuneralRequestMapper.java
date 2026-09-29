package ru.funeralagency.mapper;

import org.springframework.stereotype.Component;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestResponseDto;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.model.FuneralRequest;

import java.util.List;
import java.util.Objects;

/** Преобразует DTO заявки в доменную модель и обратно. */
@Component
public class FuneralRequestMapper {

    public FuneralRequest toEntity(FuneralRequestCreateDto dto) {
        Objects.requireNonNull(dto, "FuneralRequestCreateDto не может быть null");
        return new FuneralRequest(
                null,
                dto.getClientId(),
                normalize(dto.getDeceasedFullName()),
                dto.getCeremonyDate(),
                dto.getCeremonyType(),
                null,
                dto.getPrice(),
                null,
                normalizeNullable(dto.getComment())
        );
    }

    public FuneralRequest toEntity(FuneralRequestUpdateDto dto) {
        Objects.requireNonNull(dto, "FuneralRequestUpdateDto не может быть null");
        return new FuneralRequest(
                null,
                dto.getClientId(),
                normalize(dto.getDeceasedFullName()),
                dto.getCeremonyDate(),
                dto.getCeremonyType(),
                dto.getStatus(),
                dto.getPrice(),
                null,
                normalizeNullable(dto.getComment())
        );
    }

    public FuneralRequestResponseDto toResponseDto(FuneralRequest request) {
        Objects.requireNonNull(request, "FuneralRequest не может быть null");
        return new FuneralRequestResponseDto(
                request.getId(),
                request.getClientId(),
                request.getDeceasedFullName(),
                request.getCeremonyDate(),
                request.getCeremonyType(),
                request.getStatus(),
                request.getPrice(),
                request.getCreatedAt(),
                request.getComment()
        );
    }

    public List<FuneralRequestResponseDto> toResponseDtoList(List<FuneralRequest> requests) {
        Objects.requireNonNull(requests, "Список заявок не может быть null");
        return requests.stream().map(this::toResponseDto).toList();
    }

    public FuneralRequest toFuneralRequest(FuneralRequestCreateDto dto) {
        return toEntity(dto);
    }

    public FuneralRequest toFuneralRequest(FuneralRequestUpdateDto dto) {
        return toEntity(dto);
    }

    public FuneralRequestResponseDto toDto(FuneralRequest request) {
        return toResponseDto(request);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
