package com.atlas.backend.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Señal de vida de la tablet: cuántos ingresos tiene guardados sin subir (puede venir vacío). */
public record TotemLatidoRequestDto(
        @Min(0) @Max(100000) Integer pendientesOffline
) {}
