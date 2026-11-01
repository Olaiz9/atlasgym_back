package com.atlas.backend.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MovimientoCajaDto(
        Long id,
        String tipo,
        BigDecimal monto,
        String metodo,
        LocalDate fecha,
        LocalDateTime fechaHora,
        String registradoPor
) {
}
