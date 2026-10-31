package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;

import java.time.LocalTime;

public record TotemCheckInResponseDto(
        boolean ok,
        boolean yaRegistrado,
        String motivo,
        Long asistenciaId,
        Long alumnoId,
        String nombreAlumno,
        String alumnoDni,
        String planNombre,
        EstadoCuenta estadoCuenta,
        LocalTime horaIngreso,
        Integer asistenciasSemana,
        Integer limiteDiasSemana,
        Boolean excedeLimiteSemanal
) {
    public TotemCheckInResponseDto(
            boolean ok,
            boolean yaRegistrado,
            String motivo,
            Long asistenciaId,
            Long alumnoId,
            String nombreAlumno,
            String alumnoDni,
            String planNombre,
            EstadoCuenta estadoCuenta,
            LocalTime horaIngreso
    ) {
        this(
                ok,
                yaRegistrado,
                motivo,
                asistenciaId,
                alumnoId,
                nombreAlumno,
                alumnoDni,
                planNombre,
                estadoCuenta,
                horaIngreso,
                null,
                null,
                false
        );
    }
}
