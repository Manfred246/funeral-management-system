package ru.funeralagency.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.FuneralRequestBulkDto;
import ru.funeralagency.dto.FuneralRequestBulkResponseDto;
import ru.funeralagency.service.FuneralRequestBulkService;

@RestController
@RequestMapping("/api/requests/bulk")
public class FuneralRequestBulkController {
    private final FuneralRequestBulkService service;

    public FuneralRequestBulkController(FuneralRequestBulkService service) {
        this.service = service;
    }

    @PostMapping
    public FuneralRequestBulkResponseDto execute(@Valid @RequestBody FuneralRequestBulkDto dto) {
        return service.execute(dto);
    }
}
