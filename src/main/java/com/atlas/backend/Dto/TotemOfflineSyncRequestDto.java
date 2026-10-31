package com.atlas.backend.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TotemOfflineSyncRequestDto(
        @NotEmpty(message = "La lista de eventos offline no puede estar vacía")
        @Valid
        List<TotemOfflineSyncItemDto> eventos
) {
}
