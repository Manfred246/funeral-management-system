package ru.funeralagency.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.FuneralRequestCreateDto;
import ru.funeralagency.dto.FuneralRequestResponseDto;
import ru.funeralagency.dto.FuneralRequestUpdateDto;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.service.ExcelExporter;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.validation.FuneralRequestDtoValidator;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** REST API для работы с заявками на ритуальные услуги. */
@RestController
@RequestMapping("/api/funeral-requests")
public class FuneralRequestController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final FuneralRequestService requestService;
    private final FuneralRequestMapper requestMapper;
    private final ExcelExporter excelExporter;
    private final FuneralRequestDtoValidator requestValidator;

    public FuneralRequestController(
            FuneralRequestService requestService,
            FuneralRequestMapper requestMapper,
            ExcelExporter excelExporter,
            FuneralRequestDtoValidator requestValidator
    ) {
        this.requestService = requestService;
        this.requestMapper = requestMapper;
        this.excelExporter = excelExporter;
        this.requestValidator = requestValidator;
    }

    @PostMapping
    public ResponseEntity<FuneralRequestResponseDto> create(
            @RequestBody FuneralRequestCreateDto dto
    ) {
        requestValidator.validateForCreate(dto);
        FuneralRequest created = requestService.create(requestMapper.toEntity(dto));
        return ResponseEntity
                .created(URI.create("/api/funeral-requests/" + created.getId()))
                .body(requestMapper.toResponseDto(created));
    }

    @GetMapping
    public List<FuneralRequestResponseDto> findAll() {
        return requestMapper.toResponseDtoList(requestService.findAll());
    }

    @GetMapping("/{id}")
    public FuneralRequestResponseDto findById(@PathVariable Long id) {
        return requestMapper.toResponseDto(requestService.findById(id));
    }

    @PutMapping("/{id}")
    public FuneralRequestResponseDto update(
            @PathVariable Long id,
            @RequestBody FuneralRequestUpdateDto dto
    ) {
        requestValidator.validateForUpdate(dto);
        FuneralRequest updated = requestService.update(id, requestMapper.toEntity(dto));
        return requestMapper.toResponseDto(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        requestService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<FuneralRequestResponseDto> search(@RequestParam String query) {
        return requestMapper.toResponseDtoList(requestService.search(query));
    }

    @GetMapping("/filter")
    public List<FuneralRequestResponseDto> filter(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) CeremonyType ceremonyType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo
    ) {
        return requestMapper.toResponseDtoList(
                requestService.filter(status, ceremonyType, dateFrom, dateTo)
        );
    }

    @GetMapping("/sort")
    public List<FuneralRequestResponseDto> sort(
            @RequestParam(defaultValue = "id") String field,
            @RequestParam(defaultValue = "true") boolean ascending
    ) {
        return requestMapper.toResponseDtoList(requestService.sort(field, ascending));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] content = excelExporter.export(requestService.findAll());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("funeral-requests.xlsx")
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }
}
