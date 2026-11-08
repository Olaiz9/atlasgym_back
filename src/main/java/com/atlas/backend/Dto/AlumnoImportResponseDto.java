package com.atlas.backend.Dto;

import com.atlas.backend.Modelo.EstadoCuenta;

import java.util.List;
import java.util.Map;

/**
 * Resultado de la importación del padrón (Día Cero).
 *
 * @param simulacion true si fue un simulacro: se calculó todo igual que en la importación real,
 *                   pero no se guardó nada.
 * @param estados    cuántos socios de la planilla quedan en cada estado de cuenta (hoy).
 * @param socios     un renglón por socio de la planilla (si un DNI se repite, cuenta una vez).
 */
public record AlumnoImportResponseDto(
        int procesados,
        int creados,
        int actualizados,
        int pagosInicialesRegistrados,
        int omitidos,
        List<String> advertencias,
        boolean simulacion,
        Map<EstadoCuenta, Integer> estados,
        List<SocioImportado> socios
) {
    /**
     * @param fila   última fila de la planilla donde aparece el socio (1 = primera fila de datos).
     * @param nuevo  true si la importación lo da de alta; false si ya existía y se actualiza.
     * @param estado estado de cuenta con el que queda hoy.
     */
    public record SocioImportado(
            int fila,
            String nombre,
            String dni,
            boolean nuevo,
            String plan,
            EstadoCuenta estado
    ) {
    }
}
