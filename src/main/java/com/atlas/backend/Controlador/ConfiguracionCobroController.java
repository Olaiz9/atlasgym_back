package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.ConfiguracionCobroDto;
import com.atlas.backend.Servicio.AuthService;
import com.atlas.backend.Servicio.ConfiguracionCobroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos de transferencia del gimnasio: los ve cualquier usuario logueado, los editan los titulares. */
@RestController
@RequestMapping("/api/configuracion/cobro")
public class ConfiguracionCobroController {

    private final ConfiguracionCobroService service;
    private final AuthService authService;

    public ConfiguracionCobroController(ConfiguracionCobroService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<ConfiguracionCobroDto> obtener() {
        return ResponseEntity.ok(service.obtener());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfiguracionCobroDto> actualizar(@Valid @RequestBody ConfiguracionCobroDto dto,
                                                            Authentication authentication) {
        String operador = authentication != null && authentication.getName() != null
                ? authService.resolverEtiquetaOperador(authentication.getName())
                : "Sistema";
        return ResponseEntity.ok(service.actualizar(dto, operador));
    }
}
