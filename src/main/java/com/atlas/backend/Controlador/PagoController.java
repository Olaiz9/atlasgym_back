package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.ArqueoCajaDto;
import com.atlas.backend.Dto.PagoAnulacionRequestDto;
import com.atlas.backend.Dto.PagoCompletarSaldoRequestDto;
import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Servicio.AuthService;
import com.atlas.backend.Servicio.PagoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    public ResponseEntity<PagoResponseDto> registrarPago(
            @Valid @RequestBody PagoRequestDto dto,
            Authentication authentication
    ) {
        String operador = resolverOperador(authentication);
        PagoResponseDto pago = pagoService.registrarPago(dto, operador);
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

    @GetMapping("/arqueo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArqueoCajaDto> obtenerArqueoDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return ResponseEntity.ok(pagoService.calcularArqueoDiario(fecha));
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
            @RequestParam(required = false) String estado,
            Authentication authentication
    ) {
        String valorEstado = estado != null ? estado : (body != null ? body.get("estado") : "PAGADO");
        return ResponseEntity.ok(pagoService.actualizarEstado(id, valorEstado, resolverOperador(authentication)));
    }

    @PostMapping("/{id}/completar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDto> completarSaldoParcial(
            @PathVariable Long id,
            @RequestBody(required = false) PagoCompletarSaldoRequestDto body,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pagoService.completarSaldoParcial(id, body, resolverOperador(authentication)));
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagoResponseDto> anularPago(
            @PathVariable Long id,
            @Valid @RequestBody PagoAnulacionRequestDto body,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pagoService.anularPago(id, body.motivo(), resolverOperador(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            Authentication authentication
    ) {
        pagoService.eliminar(id, resolverOperador(authentication));
        return ResponseEntity.noContent().build();
    }

    private String resolverOperador(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "Sistema";
        }
        return authService.resolverEtiquetaOperador(authentication.getName());
    }
}
