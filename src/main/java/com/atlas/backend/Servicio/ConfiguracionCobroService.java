package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.ConfiguracionCobroDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.ConfiguracionCobro;
import com.atlas.backend.Repositorio.ConfiguracionCobroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class ConfiguracionCobroService {

    /** Alias bancario/billetera (6-20: letras, números, punto y guion) o CBU/CVU (22 dígitos). */
    private static final Pattern ALIAS_O_CBU = Pattern.compile("^([A-Za-z0-9.\\-]{6,20}|\\d{22})$");

    private final ConfiguracionCobroRepository repository;

    public ConfiguracionCobroService(ConfiguracionCobroRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ConfiguracionCobroDto obtener() {
        return repository.findById(ConfiguracionCobro.ID_UNICO)
                .map(this::toDto)
                .orElse(new ConfiguracionCobroDto(null, null, null, null));
    }

    @Transactional
    public ConfiguracionCobroDto actualizar(ConfiguracionCobroDto dto, String operador) {
        String alias = limpiar(dto != null ? dto.alias() : null);
        String titular = limpiar(dto != null ? dto.titular() : null);
        if (alias != null && !ALIAS_O_CBU.matcher(alias).matches()) {
            throw new BusinessException(
                    "El alias debe tener entre 6 y 20 caracteres (letras, números, punto o guion) o ser un CBU/CVU de 22 dígitos.");
        }
        if (alias == null && titular != null) {
            throw new BusinessException("Para indicar el titular de la cuenta también hay que cargar el alias.");
        }

        ConfiguracionCobro config = repository.findById(ConfiguracionCobro.ID_UNICO)
                .orElseGet(ConfiguracionCobro::new);
        config.setAlias(alias);
        config.setTitular(titular);
        config.setActualizadoPor(operador != null && !operador.isBlank() ? operador.trim() : "Sistema");
        config.setActualizadoEn(LocalDateTime.now());
        return toDto(repository.saveAndFlush(config));
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private ConfiguracionCobroDto toDto(ConfiguracionCobro c) {
        return new ConfiguracionCobroDto(c.getAlias(), c.getTitular(), c.getActualizadoPor(), c.getActualizadoEn());
    }
}
