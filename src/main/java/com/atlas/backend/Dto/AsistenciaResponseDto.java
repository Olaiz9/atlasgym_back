package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.MetodoAsistencia;

import java.time.LocalDate;
import java.time.LocalTime;

public record AsistenciaResponseDto(
        Long id,
        Long alumnoId,
        String nombreAlumno,
        String alumnoDni,
        String planNombre,
        LocalDate fecha,
        LocalTime hora,
        MetodoAsistencia metodo,
        EstadoCuenta estadoCuenta,
        Integer asistenciasSemana,
        Integer limiteDiasSemana,
        Boolean excedeLimiteSemanal
) {
    public AsistenciaResponseDto(
            Long id,
            Long alumnoId,
            String nombreAlumno,
            String alumnoDni,
            String planNombre,
            LocalDate fecha,
            LocalTime hora,
            MetodoAsistencia metodo,
            EstadoCuenta estadoCuenta
    ) {
        this(
                id,
                alumnoId,
                nombreAlumno,
                alumnoDni,
                planNombre,
                fecha,
                hora,
                metodo,
                estadoCuenta,
                null,
                null,
                false
        );
    }
}
