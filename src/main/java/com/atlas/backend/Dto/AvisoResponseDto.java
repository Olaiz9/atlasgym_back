package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.CategoriaAviso;

import java.time.LocalDate;

public record AvisoResponseDto(
        Long id,
        String titulo,
        String mensaje,
        CategoriaAviso categoria,
        LocalDate fecha,
        boolean paraTodos,
        Long alumnoId,
        String nombreAlumno,
        boolean leido
) {
}
