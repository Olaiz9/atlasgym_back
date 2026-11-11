package com.atlas.backend.Dto;

import java.math.BigDecimal;

public record ArqueoOperadorDto(
        String operador,
        BigDecimal totalRecaudado,
        int cantidadOperaciones
) {
}
