package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.PlanRequestDto;
import com.atlas.backend.Dto.PlanResponseDto;
import com.atlas.backend.Servicio.PlanService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/planes")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponseDto>> listarActivos() {
        return ResponseEntity.ok(planService.listarActivos());
    }

    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlanResponseDto>> listarTodos() {
        return ResponseEntity.ok(planService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponseDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(planService.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponseDto> crear(@Valid @RequestBody PlanRequestDto dto) {
        PlanResponseDto creado = planService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponseDto> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PlanRequestDto dto
    ) {
        PlanResponseDto actualizado = planService.actualizar(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        planService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
