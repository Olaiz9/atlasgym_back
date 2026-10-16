package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.GuardarSesionRequestDto;
import com.atlas.backend.Dto.SesionResponseDto;
import com.atlas.backend.Servicio.SesionEntrenamientoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/sesiones")
public class SesionEntrenamientoController {

    private final SesionEntrenamientoService sesionService;

    public SesionEntrenamientoController(SesionEntrenamientoService sesionService) {
        this.sesionService = sesionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<SesionResponseDto> guardarSesion(@Valid @RequestBody GuardarSesionRequestDto dto) {
        SesionResponseDto guardada = sesionService.guardarSesion(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }

    @GetMapping("/ultima")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<SesionResponseDto> obtenerUltimaSesion(
            @RequestParam Long alumnoId,
            @RequestParam Long diaRutinaId
    ) {
        Optional<SesionResponseDto> ultima = sesionService.obtenerUltimaSesion(alumnoId, diaRutinaId);
        return ultima.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/historial/{alumnoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<SesionResponseDto>> historialPorAlumno(@PathVariable Long alumnoId) {
        List<SesionResponseDto> historial = sesionService.historialPorAlumno(alumnoId);
        return ResponseEntity.ok(historial);
    }
}
