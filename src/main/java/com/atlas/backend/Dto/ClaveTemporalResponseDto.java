package com.atlas.backend.Dto;

/**
 * Respuesta de "Generar clave temporal": es la UNICA vez que la clave viaja en texto plano,
 * para que el titular se la pase al socio. En la base solo queda su hash (BCrypt).
 */
public record ClaveTemporalResponseDto(
        AlumnoResponseDto alumno,
        String usuario,
        String claveTemporal
) {
}
