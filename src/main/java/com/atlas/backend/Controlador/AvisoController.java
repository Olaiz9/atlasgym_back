package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.AvisoRequestDto;
import com.atlas.backend.Dto.AvisoResponseDto;
import com.atlas.backend.Servicio.AuthService;
import com.atlas.backend.Servicio.AvisoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/avisos")
public class AvisoController {

    private final AvisoService avisoService;
    private final AuthService authService;

    public AvisoController(AvisoService avisoService, AuthService authService) {
        this.avisoService = avisoService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AvisoResponseDto>> listarTodos(Authentication authentication) {
        Long usuarioId = authService.resolverUsuarioIdPorEmail(authentication.getName());
        List<AvisoResponseDto> avisos = avisoService.listarTodos(usuarioId);
        return ResponseEntity.ok(avisos);
    }

    @GetMapping("/mis-avisos")
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<List<AvisoResponseDto>> listarMisAvisos(Authentication authentication) {
        Long alumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
        Long usuarioId = authService.resolverUsuarioIdPorEmail(authentication.getName());
        List<AvisoResponseDto> avisos = avisoService.listarParaAlumno(alumnoId, usuarioId);
        return ResponseEntity.ok(avisos);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvisoResponseDto> crear(@Valid @RequestBody AvisoRequestDto dto) {
        AvisoResponseDto creado = avisoService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}/leido")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<Void> marcarComoLeido(@PathVariable Long id, Authentication authentication) {
        Long usuarioId = authService.resolverUsuarioIdPorEmail(authentication.getName());
        avisoService.marcarLeido(id, usuarioId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        avisoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
