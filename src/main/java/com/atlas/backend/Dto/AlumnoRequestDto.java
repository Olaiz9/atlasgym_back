package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlumnoRequestDto(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El DNI es obligatorio")
        @Size(min = 7, max = 20, message = "El DNI debe tener entre 7 y 20 caracteres")
        String dni,

        String email,
        String celular,
        Long planId,
        Long rutinaId,
        Boolean activo
) {
}
