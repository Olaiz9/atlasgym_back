package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.AlumnoRequestDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Servicio.AlumnoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alumnos")
public class AlumnoController {

    private final AlumnoService alumnoService;

    public AlumnoController(AlumnoService alumnoService) {
        this.alumnoService = alumnoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<AlumnoResponseDto>> listar(
            @RequestParam(required = false) Boolean soloActivos
    ) {
        if (Boolean.TRUE.equals(soloActivos)) {
            return ResponseEntity.ok(alumnoService.listarActivos());
        }
        return ResponseEntity.ok(alumnoService.listarTodos());
    }

    @GetMapping("/inactivos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlumnoResponseDto>> listarInactivos() {
        return ResponseEntity.ok(alumnoService.listarInactivos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<AlumnoResponseDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(alumnoService.buscarPorId(id));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> buscar(
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String nombre
    ) {
        if (dni != null && !dni.isBlank()) {
            return ResponseEntity.ok(alumnoService.buscarPorDni(dni.trim()));
        }
        if (nombre != null && !nombre.isBlank()) {
            return ResponseEntity.ok(alumnoService.buscarPorNombre(nombre.trim()));
        }
        return ResponseEntity.ok(alumnoService.listarTodos());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlumnoResponseDto> crear(@Valid @RequestBody AlumnoRequestDto dto) {
        AlumnoResponseDto creado = alumnoService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlumnoResponseDto> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody AlumnoRequestDto dto
    ) {
        AlumnoResponseDto actualizado = alumnoService.actualizar(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        alumnoService.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
