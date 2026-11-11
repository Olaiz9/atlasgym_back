package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record GuardarSesionRequestDto(
        @NotNull(message = "El ID del alumno es obligatorio")
        Long alumnoId,

        Long rutinaId,
        Long diaRutinaId,
        LocalDate fecha,
        Integer duracionMinutos,
        String notas,
        List<SesionEjercicioDto> ejercicios
) {
}
