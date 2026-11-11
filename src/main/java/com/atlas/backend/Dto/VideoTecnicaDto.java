package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.FormatoVideo;
import com.atlas.backend.Modelo.GrupoMuscular;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VideoTecnicaDto(
        Long id,

        @NotBlank(message = "El título del video técnico es obligatorio")
        @Size(max = 150, message = "El título no puede superar los 150 caracteres")
        String titulo,

        String descripcion,
        GrupoMuscular grupoMuscularVideo,

        @Size(max = 50, message = "El nivel no puede superar los 50 caracteres")
        String nivel,

        @Size(max = 50, message = "La duración no puede superar los 50 caracteres")
        String duracion,

        @Size(max = 500, message = "La URL del video no puede superar los 500 caracteres")
        String urlVideo,

        @Size(max = 500, message = "La URL de miniatura no puede superar los 500 caracteres")
        String thumbnailUrl,

        @Size(max = 500, message = "La URL del GIF no puede superar los 500 caracteres")
        String gifUrl,

        FormatoVideo formato,

        @Size(max = 100, message = "El equipo no puede superar los 100 caracteres")
        String equipo,

        List<String> consejosClave
) {
}
