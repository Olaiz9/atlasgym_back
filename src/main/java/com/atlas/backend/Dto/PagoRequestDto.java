package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoRequestDto(
        @NotNull(message = "El ID del alumno es obligatorio")
        Long alumnoId,

        Long planId,

        @NotNull(message = "El monto es obligatorio")
        BigDecimal monto,

        @NotBlank(message = "El método de pago es obligatorio")
        String metodo,

        @NotBlank(message = "El período del mes es obligatorio (ej: 2026-10)")
        String periodoMes,

        LocalDate fecha,

        String estado,

        Integer cantidadMeses,

        String comprobante,

        String usuarioApp,

        String passwordApp,

        BigDecimal montoAbonado
) {
    public PagoRequestDto(
            Long alumnoId,
            Long planId,
            BigDecimal monto,
            String metodo,
            String periodoMes,
            LocalDate fecha,
            String estado,
            Integer cantidadMeses,
            String comprobante,
            String usuarioApp,
            String passwordApp
    ) {
        this(
                alumnoId,
                planId,
                monto,
                metodo,
                periodoMes,
                fecha,
                estado,
                cantidadMeses,
                comprobante,
                usuarioApp,
                passwordApp,
                null
        );
    }
}
