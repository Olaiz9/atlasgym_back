package com.atlas.backend;

import com.atlas.backend.Config.TotemProduccionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TotemConfiguracionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TotemProduccionConfig.class);

    @Test
    void produccionNoArrancaSinPropiedad() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("Configure ATLAS_TOTEM_SECRET")
                            .hasStackTraceContaining("openssl rand -hex 24");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void produccionRechazaSecretoVacioOEnBlanco(String secreto) {
        runner.withPropertyValues("spring.profiles.active=prod", "atlas.totem.secret=" + secreto)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void produccionArrancaConSecretoConfigurado() {
        runner.withPropertyValues("spring.profiles.active=prod", "atlas.totem.secret=solo-para-tests")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(TotemProduccionConfig.class));
    }

    @Test
    void perfilPredeterminadoPermiteSecretoAusente() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TotemProduccionConfig.class));
    }

    @Test
    void desarrolloPermiteSecretoVacio() {
        runner.withPropertyValues("spring.profiles.active=dev", "atlas.totem.secret=")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TotemProduccionConfig.class));
    }

    @Test
    void produccionTambienValidaConOtrosPerfilesActivos() {
        runner.withPropertyValues("spring.profiles.active=dev,prod")
                .run(context -> assertThat(context).hasFailed());
    }
}