package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalTime;

public record TotemOfflineSyncItemDto(
        @NotBlank(message = "El identificador de evento offline (clientEventId) es obligatorio")
        String clientEventId,
        @NotBlank(message = "El DNI es obligatorio")
        String dni,
        LocalDate fecha,
        LocalTime hora
) {
}
