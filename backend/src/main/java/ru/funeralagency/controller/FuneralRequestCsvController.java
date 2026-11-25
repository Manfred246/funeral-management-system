package ru.funeralagency.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.funeralagency.dto.CsvApplyDto;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.dto.CsvPreviewDto;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.service.FuneralRequestCsvService;

import java.io.IOException;

@RestController
@RequestMapping("/api/requests")
public class FuneralRequestCsvController {
    private final FuneralRequestCsvService service;

    public FuneralRequestCsvController(FuneralRequestCsvService service) {
        this.service = service;
    }

    @GetMapping("/export/csv")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> export(@ParameterObject @ModelAttribute FuneralRequestFilterDto filter,
                                         @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=funeral-requests.csv")
                .body(service.export(filter, sort));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvPreviewDto preview(@RequestParam MultipartFile file, @RequestParam CsvImportMode mode) throws IOException {
        return service.preview(file.getBytes(), mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvApplyDto apply(@RequestParam MultipartFile file, @RequestParam CsvImportMode mode) throws IOException {
        return service.apply(file.getBytes(), mode);
    }
}
