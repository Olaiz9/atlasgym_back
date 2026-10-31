package com.atlas.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica en tiempo de build que todas las migraciones Flyway (V1..V4)
 * levanten desde cero y superen sin discrepancias el SchemaValidator estricto
 * de Hibernate 6 (spring.jpa.hibernate.ddl-auto=validate).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:atlas_flyway_validate;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "atlas.seed.enabled=false",
        "atlas.admin.email=dueno.prod@atlasgym.com",
        "atlas.admin.password=ClaveSeguraProd2026!"
})
class FlywayEsquemaValidacionTest {

    @Autowired
    private Flyway flyway;

    @Test
    void flywayMigraV1AV4YHibernateValidaElEsquemaCompletoSinDiscrepancias() {
        var aplicadas = flyway.info().applied();
        assertThat(aplicadas).hasSizeGreaterThanOrEqualTo(4);
        List<String> versiones = Arrays.stream(aplicadas)
                .map(info -> info.getVersion().getVersion())
                .toList();
        assertThat(versiones).containsExactly("1", "2", "3", "4");
        assertThat(Arrays.stream(aplicadas).allMatch(info -> info.getState().isApplied())).isTrue();
    }
}
