package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.AforoResponseDto;
import com.atlas.backend.Dto.AsistenciaResponseDto;
import com.atlas.backend.Dto.TotemCheckInRequestDto;
import com.atlas.backend.Dto.TotemCheckInResponseDto;
import com.atlas.backend.Servicio.AsistenciaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asistencias")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    public AsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @PostMapping("/totem")
    public ResponseEntity<TotemCheckInResponseDto> checkInTotem(@Valid @RequestBody TotemCheckInRequestDto dto) {
        TotemCheckInResponseDto respuesta = asistenciaService.checkInPorDni(dto.dni());
        return ResponseEntity.ok(respuesta);
    }
    @GetMapping("/aforo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AforoResponseDto> obtenerAforo() {
        AforoResponseDto aforo = asistenciaService.calcularAforoActual();
        return ResponseEntity.ok(aforo);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AsistenciaResponseDto>> listarPorFecha(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        List<AsistenciaResponseDto> asistencias = asistenciaService.historialPorFecha(fechaConsulta);
        return ResponseEntity.ok(asistencias);
    }

    @GetMapping("/alumno/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<AsistenciaResponseDto>> historialPorAlumno(@PathVariable Long id) {
        List<AsistenciaResponseDto> asistencias = asistenciaService.historialPorAlumno(id);
        return ResponseEntity.ok(asistencias);
    }

    @GetMapping("/distribucion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<Integer, Long>> distribucionPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        LocalDate fechaConsulta = fecha != null ? fecha : LocalDate.now();
        Map<Integer, Long> distribucion = asistenciaService.distribucionPorHora(fechaConsulta);
        return ResponseEntity.ok(distribucion);
    }
}
