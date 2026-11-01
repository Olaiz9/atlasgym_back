package com.atlas.backend.Dto;

import java.util.List;

public record AlumnoImportResponseDto(
        int procesados,
        int creados,
        int actualizados,
        int pagosInicialesRegistrados,
        int omitidos,
        List<String> advertencias
) {
}
