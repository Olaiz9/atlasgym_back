package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.FormatoVideo;
import com.atlas.backend.Modelo.GrupoMuscular;

import java.util.List;

public record VideoTecnicaDto(
        Long id,
        String titulo,
        String descripcion,
        GrupoMuscular grupoMuscularVideo,
        String nivel,
        String duracion,
        String urlVideo,
        String thumbnailUrl,
        String gifUrl,
        FormatoVideo formato,
        String equipo,
        List<String> consejosClave
) {
}
