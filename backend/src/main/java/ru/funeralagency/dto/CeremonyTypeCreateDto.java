package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CeremonyTypeCreateDto(
        @NotBlank(message = "Код типа церемонии обязателен")
        @Size(max = 50, message = "Код типа церемонии не должен превышать 50 символов") String code,
        @NotBlank(message = "Название типа церемонии обязательно")
        @Size(max = 100, message = "Название типа церемонии не должно превышать 100 символов") String name
) {
}
