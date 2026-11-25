package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CeremonyTypeUpdateDto(
        @NotBlank(message = "Название типа церемонии обязательно")
        @Size(max = 100, message = "Название типа церемонии не должно превышать 100 символов") String name
) {
}
