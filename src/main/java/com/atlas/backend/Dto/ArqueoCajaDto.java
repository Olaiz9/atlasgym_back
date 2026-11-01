package com.atlas.backend.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ArqueoCajaDto(
        LocalDate fecha,
        BigDecimal totalRecaudado,
        BigDecimal totalEfectivo,
        BigDecimal totalTransferencia,
        BigDecimal totalTarjeta,
        int cantidadCobros,
        int cantidadParciales,
        BigDecimal saldoPendienteParciales,
        int cantidadAnulados,
        BigDecimal montoAnulado,
        List<ArqueoOperadorDto> porOperador
) {
}
