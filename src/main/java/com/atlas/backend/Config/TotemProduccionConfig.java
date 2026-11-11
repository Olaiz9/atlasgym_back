package com.atlas.backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class TotemProduccionConfig {
    public TotemProduccionConfig(@Value("${atlas.totem.secret:}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalArgumentException(
                    "Configure ATLAS_TOTEM_SECRET con un secreto para la terminal; puede generarlo con openssl rand -hex 24.");
        }
    }
}