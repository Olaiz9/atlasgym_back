package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;

public record TotemPadronItemDto(
        Long alumnoId,
        String nombre,
        String dni,
        String planNombre,
        Integer limiteDiasSemana,
        EstadoCuenta estadoCuenta,
        boolean activo
) {
}
