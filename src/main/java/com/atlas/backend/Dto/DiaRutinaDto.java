package com.atlas.backend.Dto;

import java.util.List;

public record DiaRutinaDto(
        Long id,
        String nombre,
        int orden,
        List<EjercicioDto> ejercicios,
        List<Long> videoIds
) {
}
