package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;

public record TotemCheckInRequestDto(
        @NotBlank(message = "El DNI es obligatorio para registrar asistencia")
        String dni
) {
}
