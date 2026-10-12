package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.MetodoAsistencia;

import java.time.LocalDate;
import java.time.LocalTime;

public record AsistenciaResponseDto(
        Long id,
        Long alumnoId,
        String nombreAlumno,
        LocalDate fecha,
        LocalTime hora,
        MetodoAsistencia metodo,
        EstadoCuenta estadoCuenta
) {
}
