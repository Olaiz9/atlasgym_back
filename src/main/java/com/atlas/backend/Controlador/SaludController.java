package com.atlas.backend.Controlador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Endpoint público de salud (/api/salud) para monitoreo de disponibilidad,
 * conectividad con la base de datos y versión del backend.
 */
@RestController
@RequestMapping("/api/salud")
public class SaludController {

    private final JdbcTemplate jdbcTemplate;
    private final String version;
    private final String commit;

    public record SaludResponse(String estado, String base, String version, String commit) {}

    public SaludController(
            JdbcTemplate jdbcTemplate,
            Optional<BuildProperties> buildProperties,
            @Value("${atlas.version.commit:}") String commitProperty
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.version = buildProperties
                .map(BuildProperties::getVersion)
                .filter(v -> !v.isBlank())
                .map(String::trim)
                .orElse(null);
        this.commit = (commitProperty != null && !commitProperty.isBlank())
                ? commitProperty.trim()
                : null;
    }

    @GetMapping
    public ResponseEntity<SaludResponse> comprobarSalud() {
        try {
            Integer resultado = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (resultado != null && resultado == 1) {
                return ResponseEntity.ok(new SaludResponse("ok", "ok", version, commit));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new SaludResponse("error", "error", version, commit));
            }
        } catch (DataAccessException ex) {
            // Error de acceso a datos / conectividad con la base -> 503 controlado sin exponer detalles
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SaludResponse("error", "error", version, commit));
        }
    }
}
