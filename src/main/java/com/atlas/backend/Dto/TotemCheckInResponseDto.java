package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;

import java.time.LocalTime;

public record TotemCheckInResponseDto(
        boolean ok,
        boolean yaRegistrado,
        String motivo,
        String nombreAlumno,
        EstadoCuenta estadoCuenta,
        LocalTime horaIngreso
) {
}
