package com.atlas.backend.Controlador;

import com.atlas.backend.Dto.AforoResponseDto;
import com.atlas.backend.Dto.AsistenciaResponseDto;
import com.atlas.backend.Dto.TotemCheckInRequestDto;
import com.atlas.backend.Dto.TotemCheckInResponseDto;
import com.atlas.backend.Dto.TotemOfflineSyncRequestDto;
import com.atlas.backend.Dto.TotemOfflineSyncResponseDto;
import com.atlas.backend.Dto.TotemPadronItemDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Servicio.AsistenciaService;
import com.atlas.backend.Servicio.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/asistencias")
public class AsistenciaController {

    // Soporta cómodamente hora pico de gimnasio desde una misma tablet (2 check-ins/seg sostenidos)
    // bloqueando al mismo tiempo ataques automatizados de enumeración masiva de DNIs.
    private static final int MAX_CONSULTAS_TOTEM_POR_MINUTO = 120;
    private static final long VENTANA_TOTEM_MS = 60_000L;

    private record VentanaRateLimit(int contador, long inicioMs) {}

    private final ConcurrentHashMap<String, VentanaRateLimit> rateLimitTotemPorIp = new ConcurrentHashMap<>();

    private final AsistenciaService asistenciaService;
    private final AuthService authService;

    @Value("${atlas.totem.secret:}")
    private String totemSecretConfigurado;

    public AsistenciaController(AsistenciaService asistenciaService, AuthService authService) {
        this.asistenciaService = asistenciaService;
        this.authService = authService;
    }

    @PostMapping("/totem")
    public ResponseEntity<TotemCheckInResponseDto> checkInTotem(
            @Valid @RequestBody TotemCheckInRequestDto dto,
            @RequestHeader(value = "X-Totem-Secret", required = false) String totemSecretHeader,
            HttpServletRequest request
    ) {
        validarSecretoTotemSiConfigurado(totemSecretHeader);

        String clientIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "local";
        verificarRateLimitTotem(clientIp);

        TotemCheckInResponseDto respuesta = asistenciaService.checkInPorDni(dto.dni());
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/totem/padron-cache")
    public ResponseEntity<List<TotemPadronItemDto>> obtenerPadronCacheTotem(
            @RequestHeader(value = "X-Totem-Secret", required = false) String totemSecretHeader,
            Authentication authentication,
            HttpServletRequest request
    ) {
        boolean esAdminAutenticado = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean secretoValido = totemSecretConfigurado != null
                && !totemSecretConfigurado.isBlank()
                && totemSecretConfigurado.equals(totemSecretHeader);

        if (!secretoValido && !esAdminAutenticado) {
            throw new AccessDeniedException(
                    "Se requiere vincular la clave de terminal (X-Totem-Secret) para sincronizar el padrón offline."
            );
        }

        String clientIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "local";
        verificarRateLimitTotem(clientIp);

        return ResponseEntity.ok(asistenciaService.obtenerPadronMinimoTotem());
    }

    @PostMapping("/totem/sync")
    public ResponseEntity<TotemOfflineSyncResponseDto> sincronizarOutboxTotem(
            @Valid @RequestBody TotemOfflineSyncRequestDto dto,
            @RequestHeader(value = "X-Totem-Secret", required = false) String totemSecretHeader,
            HttpServletRequest request
    ) {
        validarSecretoTotemSiConfigurado(totemSecretHeader);

        String clientIp = request != null && request.getRemoteAddr() != null ? request.getRemoteAddr() : "local";
        verificarRateLimitTotem(clientIp);

        return ResponseEntity.ok(asistenciaService.sincronizarLoteOffline(dto.eventos()));
    }

    private void validarSecretoTotemSiConfigurado(String totemSecretHeader) {
        if (totemSecretConfigurado != null && !totemSecretConfigurado.isBlank()) {
            if (!totemSecretConfigurado.equals(totemSecretHeader)) {
                throw new AccessDeniedException("Dispositivo de Tótem no autorizado.");
            }
        }
    }

    private void verificarRateLimitTotem(String clientIp) {
        long ahora = System.currentTimeMillis();
        if (rateLimitTotemPorIp.size() > 1024) {
            rateLimitTotemPorIp.entrySet().removeIf(e -> (ahora - e.getValue().inicioMs()) > VENTANA_TOTEM_MS);
        }

        VentanaRateLimit ventana = rateLimitTotemPorIp.compute(clientIp, (ip, actual) -> {
            if (actual == null || (ahora - actual.inicioMs()) > VENTANA_TOTEM_MS) {
                return new VentanaRateLimit(1, ahora);
            }
            return new VentanaRateLimit(actual.contador() + 1, actual.inicioMs());
        });

        if (ventana.contador() > MAX_CONSULTAS_TOTEM_POR_MINUTO) {
            throw new BusinessException("Límite de consultas por minuto excedido en el Tótem. Aguarde unos segundos.", 429);
        }
    }

    @GetMapping("/aforo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AforoResponseDto> obtenerAforo() {
        AforoResponseDto aforo = asistenciaService.calcularAforoActual();
        return ResponseEntity.ok(aforo);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<AsistenciaResponseDto>> listarPorFecha(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication
    ) {
        boolean esAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!esAdmin && authentication != null) {
            Long miAlumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
            return ResponseEntity.ok(asistenciaService.historialPorAlumno(miAlumnoId));
        }

        if (fecha != null) {
            return ResponseEntity.ok(asistenciaService.historialPorFecha(fecha));
        }
        if (desde != null || hasta != null) {
            LocalDate desdeEfectiva = desde != null ? desde : LocalDate.now().minusDays(30);
            LocalDate hastaEfectiva = hasta != null ? hasta : LocalDate.now();
            return ResponseEntity.ok(asistenciaService.historialPorRango(desdeEfectiva, hastaEfectiva));
        }
        return ResponseEntity.ok(asistenciaService.listarTodas());
    }

    @GetMapping("/alumno/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALUMNO')")
    public ResponseEntity<List<AsistenciaResponseDto>> historialPorAlumno(
            @PathVariable Long id,
            Authentication authentication
    ) {
        boolean esAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!esAdmin && authentication != null) {
            Long miAlumnoId = authService.resolverAlumnoIdPorEmail(authentication.getName());
            if (!id.equals(miAlumnoId)) {
                throw new AccessDeniedException("Solo puede consultar sus propias asistencias.");
            }
        }
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        asistenciaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
