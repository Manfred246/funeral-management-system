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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.AdminPasswordResetDto;
import ru.funeralagency.dto.AdminUserCreateDto;
import ru.funeralagency.dto.AdminUserDto;
import ru.funeralagency.dto.AdminUserEditDto;
import ru.funeralagency.dto.AdminUserPageDto;
import ru.funeralagency.dto.AdminUserRolesDto;
import ru.funeralagency.service.AdminUserService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public AdminUserPageDto findPage(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.findPage(page, size);
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Запись создана")
    public ResponseEntity<AdminUserDto> create(@Valid @RequestBody AdminUserCreateDto dto) {
        AdminUserDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.uuid())).body(created);
    }

    @PutMapping("/{uuid}")
    public AdminUserDto edit(@PathVariable UUID uuid, @Valid @RequestBody AdminUserEditDto dto) {
        return service.edit(uuid, dto.login(), dto.name());
    }

    @PutMapping("/{uuid}/roles")
    public AdminUserDto roles(@PathVariable UUID uuid, @Valid @RequestBody AdminUserRolesDto dto) {
        return service.changeRoles(uuid, dto.roles());
    }

    @PostMapping("/{uuid}/enable")
    public AdminUserDto enable(@PathVariable UUID uuid) { return service.setEnabled(uuid, true); }

    @PostMapping("/{uuid}/disable")
    public AdminUserDto disable(@PathVariable UUID uuid) { return service.setEnabled(uuid, false); }

    @PostMapping("/{uuid}/reset-password")
    public AdminUserDto resetPassword(@PathVariable UUID uuid, @Valid @RequestBody AdminPasswordResetDto dto) {
        return service.resetPassword(uuid, dto.temporaryPassword());
    }

    @PostMapping("/{uuid}/unlock")
    public AdminUserDto unlock(@PathVariable UUID uuid) { return service.unlock(uuid); }
}
