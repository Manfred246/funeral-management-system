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
        request.setDeceasedFullName(dto.getDeceasedFullName());
        request.setCeremonyDate(dto.getCeremonyDate());
        request.setPrice(dto.getPrice());
        request.setComment(dto.getComment());
        request.setUrgent(dto.isUrgent());
        return request;
    }

    public FuneralRequest toEntity(FuneralRequestUpdateDto dto) {
        FuneralRequest request = new FuneralRequest();
        request.setDeceasedFullName(dto.getDeceasedFullName());
        request.setCeremonyDate(dto.getCeremonyDate());
        request.setStatus(dto.getStatus());
        request.setPrice(dto.getPrice());
        request.setComment(dto.getComment());
        if (dto.getUrgent() != null) {
            request.setUrgent(dto.getUrgent());
        }
        return request;
    }

    public FuneralRequestResponseDto toResponseDto(FuneralRequest request) {
        FuneralRequestResponseDto response = new FuneralRequestResponseDto(
                request.getId(), request.getClient().getId(), request.getDeceasedFullName(),
                request.getCeremonyDate(), CeremonyTypeMapper.toResponseDto(request.getCeremonyType()), request.getStatus(),
                request.getPrice(), request.getCreatedAt(), request.getComment()
        );
        response.setUuid(request.getUuid());
        response.setRequestCode(request.getRequestCode());
        response.setUrgent(request.isUrgent());
        response.setPriority(request.getPriority());
        response.setArchived(request.isArchived());
        response.setUpdatedAt(request.getUpdatedAt());
        response.setVersion(request.getVersion());
        return response;
    }

    public List<FuneralRequestResponseDto> toResponseDtoList(List<FuneralRequest> requests) {
        List<FuneralRequestResponseDto> result = new ArrayList<>();
        for (FuneralRequest request : requests) {
            result.add(toResponseDto(request));
        }
        return result;
    }
}
