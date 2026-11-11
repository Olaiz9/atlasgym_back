package com.atlas.backend.Dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ConfiguracionCobroDto(
        @Size(max = 100, message = "El alias no puede superar los 100 caracteres.")
        String alias,
        @Size(max = 150, message = "El titular no puede superar los 150 caracteres.")
        String titular,
        String actualizadoPor,
        LocalDateTime actualizadoEn
) {
}
