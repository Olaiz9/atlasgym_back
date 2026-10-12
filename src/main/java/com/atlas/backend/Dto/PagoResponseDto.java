package com.atlas.backend.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoResponseDto(
        Long id,
        Long alumnoId,
        String alumnoNombreHistorico,
        String planNombre,
        BigDecimal monto,
        LocalDate fecha,
        String metodo,
        String estado,
        String periodoMes,
        String comprobante
) {
}
