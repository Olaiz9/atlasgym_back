package com.atlas.backend.Dto;

import java.util.List;

public record AforoResponseDto(
        int sociosEnSala,
        List<String> listaSociosEnSala,
        int totalHoy
) {
}
