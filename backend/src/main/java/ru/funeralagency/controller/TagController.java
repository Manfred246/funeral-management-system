package ru.funeralagency.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.TagCreateDto;
import ru.funeralagency.dto.TagDto;
import ru.funeralagency.mapper.TagMapper;
import ru.funeralagency.service.TagService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagDto> getAll() {
        return TagMapper.toDtoList(service.getAll());
    }

    @GetMapping("/{id}")
    public TagDto getById(@PathVariable Long id) {
        return TagMapper.toDto(service.getById(id));
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Запись создана")
    public ResponseEntity<TagDto> create(@Valid @RequestBody TagCreateDto dto) {
        var created = service.create(dto.name());
        return ResponseEntity.created(URI.create("/api/tags/" + created.getId())).body(TagMapper.toDto(created));
    }
}
