package com.atlas.backend.Dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email no puede estar vacío")
        String email,

        @NotBlank(message = "La contraseña no puede estar vacía")
        String password
) {
}
