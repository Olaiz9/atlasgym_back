package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.ObjetivoRutina;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RutinaRequestDto(
        @NotBlank(message = "El nombre de la rutina es obligatorio")
        String nombre,

        String descripcion,
        ObjetivoRutina objetivo,
        Boolean esGenerica,
        List<DiaRutinaDto> dias
) {
}
