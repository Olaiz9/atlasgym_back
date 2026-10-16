package com.atlas.backend.Dto;

import java.util.List;

public record SesionEjercicioDto(
        Long id,
        Long ejercicioId,
        String nombreEjercicio,
        List<RegistroSerieDto> series
) {
}
