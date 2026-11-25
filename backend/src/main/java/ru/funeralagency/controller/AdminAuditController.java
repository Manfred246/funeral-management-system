package ru.funeralagency.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.AuditPageDto;
import ru.funeralagency.service.AuditService;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {
    private final AuditService service;

    public AdminAuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public AuditPageDto findPage(@RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                @RequestParam(defaultValue = "timestamp,desc") String sort) {
        return service.findPage(search, page, size, sort);
    }
}
