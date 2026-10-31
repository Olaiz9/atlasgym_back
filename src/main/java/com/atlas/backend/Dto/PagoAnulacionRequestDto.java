package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;

public record PagoAnulacionRequestDto(
        @NotBlank(message = "El motivo de anulación es obligatorio para la auditoría de caja.")
        String motivo
) {
}
