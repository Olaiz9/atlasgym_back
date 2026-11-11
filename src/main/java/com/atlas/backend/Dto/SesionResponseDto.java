package com.atlas.backend.Dto;

import java.time.LocalDate;
import java.util.List;

public record SesionResponseDto(
        Long id,
        Long alumnoId,
        Long rutinaId,
        Long diaRutinaId,
        String nombreDiaRutina,
        LocalDate fecha,
        Integer duracionMinutos,
        String notas,
        List<SesionEjercicioDto> ejercicios
) {
}
