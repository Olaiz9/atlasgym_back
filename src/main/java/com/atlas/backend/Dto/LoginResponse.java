package com.atlas.backend.Dto;

public record LoginResponse(
        String token,
        Long id,
        String nombre,
        String email,
        String rol,
        Long alumnoId,
        String fotoUrl,
        Boolean requiereCambioPassword
) {
    public LoginResponse(
            String token,
            Long id,
            String nombre,
            String email,
            String rol,
            Long alumnoId,
            String fotoUrl
    ) {
        this(token, id, nombre, email, rol, alumnoId, fotoUrl, false);
    }
}
