package com.atlas.backend.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PagoResponseDto(
        Long id,
        Long alumnoId,
        String alumnoNombreHistorico,
        String planNombre,
        BigDecimal monto,
        BigDecimal montoAbonado,
        BigDecimal saldoPendiente,
        LocalDate fecha,
        String metodo,
        String estado,
        String periodoMes,
        Integer cantidadMeses,
        String comprobante,
        String registradoPor,
        String anuladoPor,
        LocalDateTime fechaAnulacion,
        String motivoAnulacion
) {
    public PagoResponseDto(
            Long id,
            Long alumnoId,
            String alumnoNombreHistorico,
            String planNombre,
            BigDecimal monto,
            LocalDate fecha,
            String metodo,
            String estado,
            String periodoMes,
            Integer cantidadMeses,
            String comprobante
    ) {
        this(
                id,
                alumnoId,
                alumnoNombreHistorico,
                planNombre,
                monto,
                "PAGADO".equalsIgnoreCase(estado) ? monto : BigDecimal.ZERO,
                "PAGADO".equalsIgnoreCase(estado) ? BigDecimal.ZERO : monto,
                fecha,
                metodo,
                estado,
                periodoMes,
                cantidadMeses,
                comprobante,
                null,
                null,
                null,
                null
        );
    }
}
