package ru.funeralagency.mapper;

import org.springframework.stereotype.Component;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestResponseDto;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.model.FuneralRequest;

import java.util.ArrayList;
import java.util.List;

@Component
public class FuneralRequestMapper {
    public FuneralRequest toEntity(FuneralRequestCreateDto dto) {
        FuneralRequest request = new FuneralRequest();
        request.setClientId(dto.getClientId());
        request.setDeceasedFullName(dto.getDeceasedFullName());
        request.setCeremonyDate(dto.getCeremonyDate());
        request.setCeremonyType(dto.getCeremonyType());
        request.setPrice(dto.getPrice());
        request.setComment(dto.getComment());
        return request;
    }

    public FuneralRequest toEntity(FuneralRequestUpdateDto dto) {
        FuneralRequest request = new FuneralRequest();
        request.setClientId(dto.getClientId());
        request.setDeceasedFullName(dto.getDeceasedFullName());
        request.setCeremonyDate(dto.getCeremonyDate());
        request.setCeremonyType(dto.getCeremonyType());
        request.setStatus(dto.getStatus());
        request.setPrice(dto.getPrice());
        request.setComment(dto.getComment());
        return request;
    }

    public FuneralRequestResponseDto toResponseDto(FuneralRequest request) {
        return new FuneralRequestResponseDto(
                request.getId(), request.getClientId(), request.getDeceasedFullName(),
                request.getCeremonyDate(), request.getCeremonyType(), request.getStatus(),
                request.getPrice(), request.getCreatedAt(), request.getComment()
        );
    }

    public List<FuneralRequestResponseDto> toResponseDtoList(List<FuneralRequest> requests) {
        List<FuneralRequestResponseDto> result = new ArrayList<>();
        for (FuneralRequest request : requests) {
            result.add(toResponseDto(request));
        }
        return result;
    }
}