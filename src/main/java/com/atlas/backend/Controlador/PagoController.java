package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Servicio.AuthService;
import com.atlas.backend.Servicio.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final AuthService authService;

    public PagoController(PagoService pagoService, AuthService authService) {
        this.pagoService = pagoService;
        this.authService = authService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDto> registrarPago(@Valid @RequestBody PagoRequestDto dto) {
        PagoResponseDto pago = pagoService.registrarPago(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(pago);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<PagoResponseDto>> listarPagos(
            @RequestParam(required = false) String mes,
            Authentication authentication
    ) {
        boolean esAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!esAdmin && authentication != null) {
            Long miAlumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
            return ResponseEntity.ok(pagoService.listarPorAlumno(miAlumnoId));
        }

        if (mes != null && !mes.isBlank()) {
            return ResponseEntity.ok(pagoService.listarPorMes(mes.trim()));
        }
        return ResponseEntity.ok(pagoService.listarTodos());
    }

    @GetMapping("/alumno/{alumnoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<PagoResponseDto>> listarPorAlumno(
            @PathVariable Long alumnoId, Authentication authentication) {
        boolean esAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!esAdmin) {
            Long miAlumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
            if (!alumnoId.equals(miAlumnoId)) {
                throw new AccessDeniedException("Solo puede consultar sus propios pagos.");
            }
        }
        return ResponseEntity.ok(pagoService.listarPorAlumno(alumnoId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pagoService.buscarPorId(id));
    }

    @RequestMapping(value = "/{id}/estado", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDto> actualizarEstado(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String estado
    ) {
        String valorEstado = estado != null ? estado : (body != null ? body.get("estado") : "PAGADO");
        return ResponseEntity.ok(pagoService.actualizarEstado(id, valorEstado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pagoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
