package ru.funeralagency.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.CeremonyTypeCreateDto;
import ru.funeralagency.dto.CeremonyTypeResponseDto;
import ru.funeralagency.dto.CeremonyTypeUpdateDto;
import ru.funeralagency.mapper.CeremonyTypeMapper;
import ru.funeralagency.service.CeremonyTypeService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/ceremony-types")
public class CeremonyTypeController {
    private final CeremonyTypeService service;

    public CeremonyTypeController(CeremonyTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<CeremonyTypeResponseDto> getAll() {
        return CeremonyTypeMapper.toResponseDtoList(service.getAll());
    }

    @GetMapping("/{id}")
    public CeremonyTypeResponseDto getById(@PathVariable Long id) {
        return CeremonyTypeMapper.toResponseDto(service.getById(id));
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Запись создана")
    public ResponseEntity<CeremonyTypeResponseDto> create(@Valid @RequestBody CeremonyTypeCreateDto dto) {
        var created = service.create(dto.code(), dto.name());
        return ResponseEntity.created(URI.create("/api/ceremony-types/" + created.getId()))
                .body(CeremonyTypeMapper.toResponseDto(created));
    }

    @PutMapping("/{id}")
    public CeremonyTypeResponseDto update(@PathVariable Long id, @Valid @RequestBody CeremonyTypeUpdateDto dto) {
        return CeremonyTypeMapper.toResponseDto(service.update(id, dto.name()));
    }

    @PostMapping("/{id}/enable")
    public CeremonyTypeResponseDto enable(@PathVariable Long id) {
        return CeremonyTypeMapper.toResponseDto(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    public CeremonyTypeResponseDto disable(@PathVariable Long id) {
        return CeremonyTypeMapper.toResponseDto(service.disable(id));
    }
}
