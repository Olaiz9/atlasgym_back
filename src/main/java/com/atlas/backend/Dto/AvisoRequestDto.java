package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.CategoriaAviso;
import jakarta.validation.constraints.NotBlank;

public record AvisoRequestDto(
        @NotBlank(message = "El título es obligatorio")
        String titulo,

        @NotBlank(message = "El mensaje es obligatorio")
        String mensaje,

        CategoriaAviso categoria,
        Boolean paraTodos,
        Long alumnoId
) {
}
