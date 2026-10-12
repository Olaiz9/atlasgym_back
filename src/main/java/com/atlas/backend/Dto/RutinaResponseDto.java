package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.ObjetivoRutina;

import java.util.List;

public record RutinaResponseDto(
        Long id,
        String nombre,
        String descripcion,
        ObjetivoRutina objetivo,
        boolean esGenerica,
        List<DiaRutinaDto> dias
) {
}
