package com.atlas.backend.Excepciones;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    public ResourceNotFoundException(String entidad, Long id) {
        super(String.format("%s no encontrado con ID: %d", entidad, id));
    }
}
