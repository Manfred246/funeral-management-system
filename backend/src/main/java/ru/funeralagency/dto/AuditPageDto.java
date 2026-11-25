package ru.funeralagency.dto;

import java.util.List;

public record AuditPageDto(List<AuditEventDto> content, int page, int size, long totalElements, int totalPages) {
}
