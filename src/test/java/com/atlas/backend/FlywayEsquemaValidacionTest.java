package com.atlas.backend;

import com.atlas.backend.Repositorio.UsuarioRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica en tiempo de build que todas las migraciones Flyway (V1..V6)
 * levanten desde cero y superen sin discrepancias el SchemaValidator estricto
 * de Hibernate 6 (spring.jpa.hibernate.ddl-auto=validate).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=${atlas.test.flyway.url:jdbc:h2:mem:atlas_flyway_validate;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "atlas.seed.enabled=false",
        "atlas.admin.bootstrap.enabled=true",
        "atlas.admin.bootstrap.email=dueno.prod@atlasgym.com",
        "atlas.admin.bootstrap.password=ClaveSeguraProd2026!"
})
class FlywayEsquemaValidacionTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void limpiarUsuarioBootstrap() {
        usuarioRepository.findByEmailIgnoreCase("dueno.prod@atlasgym.com")
                .ifPresent(usuarioRepository::delete);
    }

    @Test
    void flywayMigraV1AV8YHibernateValidaElEsquemaCompletoSinDiscrepancias() {
        var aplicadas = flyway.info().applied();
        assertThat(aplicadas).hasSizeGreaterThanOrEqualTo(8);
        List<String> versiones = Arrays.stream(aplicadas)
                .map(info -> info.getVersion().getVersion())
                .toList();
        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(Arrays.stream(aplicadas).allMatch(info -> info.getState().isApplied())).isTrue();
    }
}
