package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlanRequestDto(
        @NotBlank(message = "El nombre del plan es obligatorio")
        String nombre,

        @NotNull(message = "El precio es obligatorio")
        BigDecimal precio,

        String descripcion,
        String diaPorSemana,
        Boolean activo
) {
}
