package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.RutinaRequestDto;
import com.atlas.backend.Dto.RutinaResponseDto;
import com.atlas.backend.Servicio.AuthService;
import com.atlas.backend.Servicio.RutinaService;
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
@RequestMapping("/api/rutinas")
public class RutinaController {

    private final RutinaService rutinaService;
    private final AuthService authService;

    public RutinaController(RutinaService rutinaService, AuthService authService) {
        this.rutinaService = rutinaService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RutinaResponseDto>> listarTodas() {
        return ResponseEntity.ok(rutinaService.listarTodas());
    }

    @GetMapping("/genericas")
    public ResponseEntity<List<RutinaResponseDto>> listarGenericas() {
        return ResponseEntity.ok(rutinaService.listarGenericas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RutinaResponseDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(rutinaService.buscarPorId(id));
    }

    @GetMapping("/mis-rutinas")
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<RutinaResponseDto> obtenerMiRutina(Authentication authentication) {
        Long alumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
        RutinaResponseDto rutina = rutinaService.obtenerRutinaDeAlumno(alumnoId);
        if (rutina == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(rutina);
    }

    @GetMapping("/alumno/{alumnoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<RutinaResponseDto> obtenerRutinaDeAlumno(@PathVariable Long alumnoId) {
        RutinaResponseDto rutina = rutinaService.obtenerRutinaDeAlumno(alumnoId);
        if (rutina == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(rutina);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RutinaResponseDto> crear(@Valid @RequestBody RutinaRequestDto dto) {
        RutinaResponseDto creada = rutinaService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RutinaResponseDto> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RutinaRequestDto dto
    ) {
        RutinaResponseDto actualizada = rutinaService.actualizar(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @PostMapping("/{rutinaId}/asignar/{alumnoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> asignarAAlumno(@PathVariable Long rutinaId, @PathVariable Long alumnoId) {
        rutinaService.asignarAAlumno(rutinaId, alumnoId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/asignar/{alumnoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desasignarDeAlumno(@PathVariable Long alumnoId) {
        rutinaService.desasignarDeAlumno(alumnoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rutinaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
