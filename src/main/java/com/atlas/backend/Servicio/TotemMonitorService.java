package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.TotemEstadoDto;
import com.atlas.backend.Modelo.TotemLatido;
import com.atlas.backend.Repositorio.TotemLatidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Sabe si la tablet del Tótem sigue andando. La tablet manda una señal por minuto
 * ({@link #registrarLatido}); si deja de llegar en horario de apertura, el estado pasa a SIN_SENAL
 * y la app de los titulares muestra el aviso. Fuera de horario no se alarma (de noche la tablet
 * puede estar apagada a propósito).
 */
@Service
public class TotemMonitorService {

    private static final Logger log = LoggerFactory.getLogger(TotemMonitorService.class);

    public static final String EN_LINEA = "EN_LINEA";
    public static final String SIN_SENAL = "SIN_SENAL";
    public static final String FUERA_DE_HORARIO = "FUERA_DE_HORARIO";
    public static final String NUNCA_CONECTADO = "NUNCA_CONECTADO";

    private final TotemLatidoRepository repository;
    private final int minutosAlerta;
    private final LocalTime apertura;
    private final LocalTime cierre;

    public TotemMonitorService(
            TotemLatidoRepository repository,
            @Value("${atlas.totem.alerta-minutos:5}") int minutosAlerta,
            @Value("${atlas.totem.horario:06:00-23:00}") String horario
    ) {
        this.repository = repository;
        this.minutosAlerta = Math.max(1, minutosAlerta);
        LocalTime[] rango = parsearHorario(horario);
        this.apertura = rango[0];
        this.cierre = rango[1];
    }

    @Transactional
    public void registrarLatido(Integer pendientesOffline, LocalDateTime ahora) {
        TotemLatido latido = repository.findById(TotemLatido.ID_UNICO).orElseGet(TotemLatido::new);
        latido.setUltimoLatido(ahora);
        latido.setPendientesOffline(pendientesOffline == null ? 0 : Math.max(0, pendientesOffline));
        repository.save(latido);
    }

    @Transactional(readOnly = true)
    public TotemEstadoDto obtenerEstado(LocalDateTime ahora) {
        TotemLatido latido = repository.findById(TotemLatido.ID_UNICO).orElse(null);
        if (latido == null || latido.getUltimoLatido() == null) {
            return new TotemEstadoDto(NUNCA_CONECTADO, null, null, 0, minutosAlerta);
        }
        long minutos = Math.max(0, Duration.between(latido.getUltimoLatido(), ahora).toMinutes());
        String estado;
        if (minutos < minutosAlerta) {
            estado = EN_LINEA;
        } else if (dentroDeHorario(ahora.toLocalTime())) {
            estado = SIN_SENAL;
        } else {
            estado = FUERA_DE_HORARIO;
        }
        return new TotemEstadoDto(estado, latido.getUltimoLatido(), minutos, latido.getPendientesOffline(), minutosAlerta);
    }

    /** true si la hora cae en el horario de apertura. Soporta horarios que cruzan la medianoche (18:00-02:00). */
    public boolean dentroDeHorario(LocalTime hora) {
        if (apertura.equals(cierre)) {
            return true; // abierto las 24 hs
        }
        if (apertura.isBefore(cierre)) {
            return !hora.isBefore(apertura) && hora.isBefore(cierre);
        }
        return !hora.isBefore(apertura) || hora.isBefore(cierre);
    }

    /** "HH:mm-HH:mm". Si viene mal escrito, se alarma a toda hora (mejor un aviso de más que uno de menos). */
    static LocalTime[] parsearHorario(String horario) {
        try {
            String[] partes = horario == null ? new String[0] : horario.trim().split("\\s*-\\s*");
            if (partes.length == 2) {
                return new LocalTime[]{LocalTime.parse(partes[0]), LocalTime.parse(partes[1])};
            }
        } catch (DateTimeParseException ignorada) {
            // cae al aviso de abajo
        }
        log.warn("ATLAS_TOTEM_HORARIO='{}' no tiene el formato HH:mm-HH:mm; el aviso de tótem sin señal queda activo las 24 hs.", horario);
        return new LocalTime[]{LocalTime.MIDNIGHT, LocalTime.MIDNIGHT};
    }
}
