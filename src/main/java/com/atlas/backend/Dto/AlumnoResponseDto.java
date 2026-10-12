package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;

import java.time.LocalDate;

public record AlumnoResponseDto(
        Long id,
        String nombre,
        String dni,
        String email,
        String celular,
        Long planId,
        String planNombre,
        LocalDate fechaAlta,
        Boolean activo,
        EstadoCuenta estadoCuenta,
        Long rutinaId,
        String rutinaNombre
) {
}
