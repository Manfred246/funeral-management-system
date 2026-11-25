package ru.funeralagency.dto;

import java.util.List;

public record AdminUserPageDto(List<AdminUserDto> content, int page, int size, long totalElements, int totalPages) {
}
