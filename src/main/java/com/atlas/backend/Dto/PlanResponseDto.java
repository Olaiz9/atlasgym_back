package com.atlas.backend.Dto;

import java.math.BigDecimal;

public record PlanResponseDto(
        Long id,
        String nombre,
        BigDecimal precio,
        String descripcion,
        String diaPorSemana,
        boolean activo
) {
}
