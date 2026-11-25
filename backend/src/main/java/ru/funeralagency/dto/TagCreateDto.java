package ru.funeralagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagCreateDto(
        @NotBlank(message = "Имя тега обязательно")
        @Size(max = 50, message = "Имя тега не должно превышать 50 символов") String name
) {
}
