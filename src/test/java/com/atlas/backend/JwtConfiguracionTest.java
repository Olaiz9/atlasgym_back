package com.atlas.backend;

import com.atlas.backend.Seguridad.JwtService;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfiguracionTest {
    // Claves publicas exclusivas de pruebas; nunca configurar una de ellas en un servidor.
    private static final String CLAVE_A = "test-only-a-0123456789012345678901234567890123456789";
    private static final String CLAVE_B = "test-only-b-0123456789012345678901234567890123456789";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "demasiado-corta"})
    void rechazaClaveAusenteVaciaOCorta(String clave) {
        assertThatThrownBy(() -> new JwtService(clave, 60000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATLAS_JWT_SECRET");
    }

    @Test
    void contextoNoArrancaSinClave() {
        new ApplicationContextRunner().withUserConfiguration(JwtService.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextoArrancaConClaveExplicita() {
        new ApplicationContextRunner().withUserConfiguration(JwtService.class)
                .withPropertyValues("atlas.jwt.secret=" + CLAVE_A)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtService.class));
    }

    @Test
    void tokenFirmadoConClaveActualSePuedeVerificar() {
        JwtService servicio = new JwtService(CLAVE_A, 60000);
        String token = servicio.generarToken(1L, "socio-test", "ALUMNO", 10L);
        assertThat(servicio.extraerEmail(token)).isEqualTo("socio-test");
        assertThat(servicio.extraerAlumnoId(token)).isEqualTo(10L);
    }

    @Test
    void rotarClaveRechazaTokensAnteriores() {
        JwtService anterior = new JwtService(CLAVE_A, 60000);
        JwtService actual = new JwtService(CLAVE_B, 60000);
        String tokenAnterior = anterior.generarToken(1L, "socio-test", "ALUMNO", 10L);
        assertThatThrownBy(() -> actual.extraerEmail(tokenAnterior))
                .isInstanceOf(SignatureException.class);
        String tokenActual = actual.generarToken(1L, "socio-test", "ALUMNO", 10L);
        assertThat(actual.extraerEmail(tokenActual)).isEqualTo("socio-test");
    }
}
