package ru.funeralagency.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.RequestNoteResponseDto;
import ru.funeralagency.dto.RequestNoteWriteDto;
import ru.funeralagency.security.AuthenticatedUser;
import ru.funeralagency.service.RequestNoteService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests/{uuid}/notes")
public class RequestNoteController {
    private final RequestNoteService service;

    public RequestNoteController(RequestNoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<RequestNoteResponseDto> getAll(@PathVariable UUID uuid) {
        return service.getAll(uuid);
    }

    @PostMapping
    public ResponseEntity<RequestNoteResponseDto> create(@PathVariable UUID uuid,
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RequestNoteWriteDto dto) {
        var created = service.create(uuid, user.uuid(), dto.text(), dto.pinned());
        return ResponseEntity.created(URI.create("/api/requests/" + uuid + "/notes/" + created.uuid())).body(created);
    }

    @PutMapping("/{noteUuid}")
    public RequestNoteResponseDto update(@PathVariable UUID uuid, @PathVariable UUID noteUuid,
                                         @Valid @RequestBody RequestNoteWriteDto dto) {
        return service.update(uuid, noteUuid, dto.text(), dto.pinned());
    }

    @DeleteMapping("/{noteUuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid, @PathVariable UUID noteUuid) {
        service.delete(uuid, noteUuid);
        return ResponseEntity.noContent().build();
    }
}
