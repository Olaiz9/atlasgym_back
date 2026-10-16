package com.atlas.backend.Dto;

public record RegistroSerieDto(
        Long id,
        int serieNumero,
        Double kg,
        Integer reps,
        Boolean completada
) {
}
