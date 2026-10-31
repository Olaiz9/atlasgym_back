package com.atlas.backend.Dto;

import java.math.BigDecimal;

public record AlumnoImportItemDto(
        String nombre,
        String dni,
        String celular,
        String email,
        String planNombre,
        String ultimoPeriodoPago,
        BigDecimal montoUltimoPago,
        String metodoPago
) {
}
