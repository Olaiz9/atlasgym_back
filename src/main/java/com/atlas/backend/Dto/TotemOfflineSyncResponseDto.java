package com.atlas.backend.Dto;

import java.util.List;

public record TotemOfflineSyncResponseDto(
        int recibidos,
        int registradosNuevos,
        int yaExistentes,
        int rechazados,
        List<String> clientEventIdsProcesados
) {
}
