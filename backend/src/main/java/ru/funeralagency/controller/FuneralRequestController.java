package ru.funeralagency.controller;

import jakarta.validation.Valid;
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
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.dto.FuneralRequestPageDto;
import ru.funeralagency.dto.TagDto;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.service.ExcelExporter;
import ru.funeralagency.service.FuneralRequestService;

import java.net.URI;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class FuneralRequestController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final FuneralRequestService requestService;
    private final FuneralRequestMapper requestMapper;
    private final ExcelExporter excelExporter;

    public FuneralRequestController(
            FuneralRequestService requestService,
            FuneralRequestMapper requestMapper,
            ExcelExporter excelExporter
    ) {
        this.requestService = requestService;
        this.requestMapper = requestMapper;
        this.excelExporter = excelExporter;
    }

    @PostMapping
    public ResponseEntity<FuneralRequestResponseDto> create(
            @Valid @RequestBody FuneralRequestCreateDto dto
    ) {
        FuneralRequest created = requestService.create(
                requestMapper.toEntity(dto), dto.getClientId(), dto.getCeremonyTypeId());
        return ResponseEntity
                .created(URI.create("/api/requests/" + created.getUuid()))
                .body(requestMapper.toResponseDto(created));
    }

    @GetMapping
    public FuneralRequestPageDto findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long client,
            @RequestParam(required = false) Long ceremonyTypeId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Boolean urgent,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) Long tag,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setQ(q);
        filter.setClient(client);
        filter.setCeremonyTypeId(ceremonyTypeId);
        filter.setStatus(status);
        filter.setUrgent(urgent);
        filter.setPriority(priority);
        filter.setTag(tag);
        filter.setArchived(archived);
        filter.setMinPrice(minPrice);
        filter.setMaxPrice(maxPrice);
        return requestService.findPage(filter, page, size, sort);
    }

    @GetMapping("/{uuid}")
    public FuneralRequestResponseDto getByUuid(@PathVariable UUID uuid) {
        return requestMapper.toResponseDto(requestService.getByUuid(uuid));
    }

    @GetMapping("/{uuid}/tags")
    public List<TagDto> getTags(@PathVariable UUID uuid) {
        return requestService.getTagsByUuid(uuid);
    }

    @PostMapping("/{uuid}/tags/{tagId}")
    public ResponseEntity<Void> addTag(@PathVariable UUID uuid, @PathVariable Long tagId) {
        requestService.addTagByUuid(uuid, tagId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{uuid}/tags/{tagId}")
    public ResponseEntity<Void> removeTag(@PathVariable UUID uuid, @PathVariable Long tagId) {
        requestService.removeTagByUuid(uuid, tagId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uuid}")
    public FuneralRequestResponseDto update(
            @PathVariable UUID uuid,
            @Valid @RequestBody FuneralRequestUpdateDto dto
    ) {
        FuneralRequest updated = requestService.updateByUuid(
                uuid, requestMapper.toEntity(dto), dto.getClientId(), dto.getCeremonyTypeId(), dto.getUrgent(), dto.getVersion());
        return requestMapper.toResponseDto(updated);
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> archive(@PathVariable UUID uuid) {
        requestService.archiveByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/restore")
    public FuneralRequestResponseDto restore(@PathVariable UUID uuid) {
        return requestMapper.toResponseDto(requestService.restoreByUuid(uuid));
    }

    @DeleteMapping("/{uuid}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable UUID uuid) {
        requestService.deletePermanentlyByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<FuneralRequestResponseDto> search(
            @RequestParam(required = false) String deceasedName,
            @RequestParam(required = false) Long clientId
    ) {
        return requestMapper.toResponseDtoList(requestService.search(deceasedName, clientId));
    }

    @GetMapping("/filter")
    public List<FuneralRequestResponseDto> filter(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String ceremonyType,
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
            @RequestParam(defaultValue = "ceremonyDate") String field,
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
