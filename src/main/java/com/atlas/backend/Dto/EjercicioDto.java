package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.TipoSerieEjercicio;

public record EjercicioDto(
        Long id,
        String nombreEjercicio,
        int serie,
        String repeticiones,
        String descansoSegundos,
        String notas,
        int orden,
        TipoSerieEjercicio tipoSerie,
        Long videoTecnicaId,
        String videoTecnicaTitulo
) {
}
