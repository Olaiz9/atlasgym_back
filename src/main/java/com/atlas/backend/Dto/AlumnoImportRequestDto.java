package com.atlas.backend.Dto;

import java.util.List;

public record AlumnoImportRequestDto(
        List<AlumnoImportItemDto> filas
) {
}
