package com.atlas.backend.Dto;

public record PagoCompletarSaldoRequestDto(
        String metodo,
        String comprobante
) {
}
