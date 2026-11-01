package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AforoResponseDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Dto.AsistenciaResponseDto;
import com.atlas.backend.Dto.TotemCheckInResponseDto;
import com.atlas.backend.Dto.TotemOfflineSyncItemDto;
import com.atlas.backend.Dto.TotemOfflineSyncResponseDto;
import com.atlas.backend.Dto.TotemPadronItemDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.MetodoAsistencia;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AsistenciaService {

    private static final Pattern PATRON_DIAS_SEMANA = Pattern.compile("([1-7])");

    private final AsistenciaRepository asistenciaRepository;
    private final AlumnoRepository alumnoRepository;
    private final AlumnoService alumnoService;

    public AsistenciaService(AsistenciaRepository asistenciaRepository,
                             AlumnoRepository alumnoRepository,
                             AlumnoService alumnoService) {
        this.asistenciaRepository = asistenciaRepository;
        this.alumnoRepository = alumnoRepository;
        this.alumnoService = alumnoService;
    }

    /**
     * Extrae el límite numérico de días por semana del plan (ej: "3 días por semana" -> 3).
     * Retorna null si el plan es "Libre", ilimitado o no define tope semanal.
     */
    public static Integer extraerLimiteDiasSemana(String diaPorSemana) {
        if (diaPorSemana == null || diaPorSemana.isBlank()) {
            return null;
        }
        String limpio = diaPorSemana.trim().toLowerCase(Locale.ROOT);
        if (limpio.contains("libre") || limpio.contains("ilimitad") || limpio.contains("full")) {
            return null;
        }
        Matcher matcher = PATRON_DIAS_SEMANA.matcher(limpio);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * Devuelve el lunes de la semana ISO correspondiente a la fecha dada.
     */
    public static LocalDate inicioSemanaLunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Valida el ingreso de un socio en el Kiosco Tótem por DNI.
     * Regla Anti-fraude: Si el alumno ya ingresó hoy y transcurrieron menos de 100 minutos,
     * rechaza la entrada duplicada con "Tu sesión continúa activa."
     * No bloquea por cuota ni por exceso de días semanales: registra e informa al módulo de asistencias.
     */
    @Transactional
    public TotemCheckInResponseDto checkInPorDni(String dni) {
        String dniNormalizado = dni != null ? dni.replaceAll("\\D", "").trim() : "";
        Optional<Alumno> alumnoOpt = alumnoRepository.findByDniWithRelaciones(dniNormalizado);

        if (alumnoOpt.isEmpty()) {
            return new TotemCheckInResponseDto(
                    false,
                    false,
                    "DNI no encontrado en el sistema.",
                    null,
                    null,
                    null,
                    dniNormalizado,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        Alumno alumno = alumnoOpt.get();
        Plan plan = alumno.getPlanAlumno();
        String planNombre = plan != null ? plan.getNombre() : "Pase Libre";
        Integer limiteDiasSemana = plan != null ? extraerLimiteDiasSemana(plan.getDiaPorSemana()) : null;

        if (!alumno.isActivo()) {
            return new TotemCheckInResponseDto(
                    false,
                    false,
                    "Socio inactivo en el sistema.",
                    null,
                    alumno.getIdAlumno(),
                    alumno.getNombre(),
                    alumno.getDni(),
                    planNombre,
                    EstadoCuenta.INACTIVO,
                    null,
                    0,
                    limiteDiasSemana,
                    false
            );
        }

        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        LocalDate lunesSemana = inicioSemanaLunes(hoy);
        LocalDate domingoSemana = lunesSemana.plusDays(6);

        List<Asistencia> asistenciasHoy = asistenciaRepository
                .findByAlumnoIdAndFechaOrderByHoraDesc(alumno.getIdAlumno(), hoy);

        if (!asistenciasHoy.isEmpty()) {
            Asistencia ultima = asistenciasHoy.get(0);
            if (ultima.getHora() != null) {
                long minutosTranscurridos = Duration.between(ultima.getHora(), ahora).toMinutes();
                if (minutosTranscurridos >= 0 && minutosTranscurridos < 100) {
                    int diasAsistidos = asistenciaRepository
                            .findFechasDistintasByAlumnoIdAndRango(alumno.getIdAlumno(), lunesSemana, domingoSemana)
                            .size();
                    boolean excede = limiteDiasSemana != null && diasAsistidos > limiteDiasSemana;
                    return new TotemCheckInResponseDto(
                            false,
                            true,
                            "Tu sesión continúa activa.",
                            ultima.getId(),
                            alumno.getIdAlumno(),
                            alumno.getNombre(),
                            alumno.getDni(),
                            planNombre,
                            ultima.getEstadoCuenta(),
                            ultima.getHora(),
                            diasAsistidos,
                            limiteDiasSemana,
                            excede
                    );
                }
            }
        }

        EstadoCuenta estadoCuentaSnapshot = alumnoService.calcularEstadoCuenta(alumno);

        Asistencia nuevaAsistencia = new Asistencia(
                hoy,
                ahora,
                alumno,
                MetodoAsistencia.DNI_TOTEM,
                estadoCuentaSnapshot
        );
        Asistencia guardada = asistenciaRepository.saveAndFlush(nuevaAsistencia);

        alumno.setUltimaAsistencia(hoy);
        alumnoRepository.save(alumno);

        int diasAsistidosSemana = asistenciaRepository
                .findFechasDistintasByAlumnoIdAndRango(alumno.getIdAlumno(), lunesSemana, domingoSemana)
                .size();
        boolean excedeLimiteSemanal = limiteDiasSemana != null && diasAsistidosSemana > limiteDiasSemana;

        return new TotemCheckInResponseDto(
                true,
                false,
                "Ingreso registrado correctamente.",
                guardada.getId(),
                alumno.getIdAlumno(),
                alumno.getNombre(),
                alumno.getDni(),
                planNombre,
                estadoCuentaSnapshot,
                ahora,
                diasAsistidosSemana,
                limiteDiasSemana,
                excedeLimiteSemanal
        );
    }

    /**
     * Devuelve el padrón mínimo estrictamente necesario para que la tablet del Tótem
     * opere sin conexión durante cortes de Internet (sin exponer emails, celulares ni claves).
     */
    @Transactional(readOnly = true)
    public List<TotemPadronItemDto> obtenerPadronMinimoTotem() {
        List<Alumno> entidades = alumnoRepository.findAllWithRelaciones();
        Map<Long, Plan> planPorAlumnoId = new HashMap<>();
        for (Alumno a : entidades) {
            if (a.getIdAlumno() != null && a.getPlanAlumno() != null) {
                planPorAlumnoId.put(a.getIdAlumno(), a.getPlanAlumno());
            }
        }
        List<AlumnoResponseDto> calculados = alumnoService.listarTodos();
        List<TotemPadronItemDto> resultado = new ArrayList<>(calculados.size());
        for (AlumnoResponseDto dto : calculados) {
            if (dto.dni() == null || dto.dni().isBlank()) continue;
            Plan plan = planPorAlumnoId.get(dto.id());
            Integer limiteDias = plan != null ? extraerLimiteDiasSemana(plan.getDiaPorSemana()) : null;
            resultado.add(new TotemPadronItemDto(
                    dto.id(),
                    dto.nombre(),
                    dto.dni().replaceAll("\\D", "").trim(),
                    dto.planNombre() != null ? dto.planNombre() : "Pase Libre",
                    limiteDias,
                    dto.estadoCuenta(),
                    dto.activo()
            ));
        }
        return resultado;
    }

    /**
     * Sincroniza de forma idempotente la cola Outbox de ingresos registrados offline en el Tótem.
     * Si la tablet reintenta el mismo lote por red inestable, `clientEventId` (UNIQUE en MySQL)
     * y la ventana anti-duplicado de 100 minutos garantizan cero ingresos duplicados.
     */
    @Transactional
    public TotemOfflineSyncResponseDto sincronizarLoteOffline(List<TotemOfflineSyncItemDto> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return new TotemOfflineSyncResponseDto(0, 0, 0, 0, List.of());
        }

        int nuevos = 0;
        int existentes = 0;
        int rechazados = 0;
        List<String> procesados = new ArrayList<>(eventos.size());

        for (TotemOfflineSyncItemDto ev : eventos) {
            if (ev == null || ev.clientEventId() == null || ev.clientEventId().isBlank()) {
                rechazados++;
                continue;
            }
            String eventId = ev.clientEventId().trim();
            if (eventId.length() > 64) {
                eventId = eventId.substring(0, 64);
            }

            if (asistenciaRepository.findByClientEventId(eventId).isPresent()) {
                existentes++;
                procesados.add(ev.clientEventId());
                continue;
            }

            String dniNormalizado = ev.dni() != null ? ev.dni().replaceAll("\\D", "").trim() : "";
            Optional<Alumno> alumnoOpt = alumnoRepository.findByDniWithRelaciones(dniNormalizado);
            if (alumnoOpt.isEmpty() || !alumnoOpt.get().isActivo()) {
                rechazados++;
                procesados.add(ev.clientEventId());
                continue;
            }

            Alumno alumno = alumnoOpt.get();
            LocalDate fechaEvento = ev.fecha() != null ? ev.fecha() : LocalDate.now();
            LocalTime horaEvento = ev.hora() != null ? ev.hora() : LocalTime.now();

            List<Asistencia> delDia = asistenciaRepository
                    .findByAlumnoIdAndFechaOrderByHoraDesc(alumno.getIdAlumno(), fechaEvento);
            boolean duplicadoVentana = delDia.stream()
                    .filter(a -> a.getHora() != null)
                    .anyMatch(a -> Math.abs(Duration.between(a.getHora(), horaEvento).toMinutes()) < 100);

            if (duplicadoVentana) {
                existentes++;
                procesados.add(ev.clientEventId());
                continue;
            }

            EstadoCuenta estadoSnapshot = alumnoService.calcularEstadoCuenta(alumno);
            Asistencia nueva = new Asistencia(
                    fechaEvento,
                    horaEvento,
                    alumno,
                    MetodoAsistencia.DNI_TOTEM,
                    estadoSnapshot
            );
            nueva.setClientEventId(eventId);
            asistenciaRepository.saveAndFlush(nueva);

            if (alumno.getUltimaAsistencia() == null || fechaEvento.isAfter(alumno.getUltimaAsistencia())) {
                alumno.setUltimaAsistencia(fechaEvento);
                alumnoRepository.save(alumno);
            }

            nuevos++;
            procesados.add(ev.clientEventId());
        }

        return new TotemOfflineSyncResponseDto(
                eventos.size(),
                nuevos,
                existentes,
                rechazados,
                procesados
        );
    }

    /**
     * Calcula en tiempo real cuántos socios se encuentran actualmente en sala
     * (socios con check-in en los últimos 100 minutos).
     */
    @Transactional(readOnly = true)
    public AforoResponseDto calcularAforoActual() {
        LocalDate hoy = LocalDate.now();
        LocalTime horaCorte = LocalTime.now().minusMinutes(100);

        List<Asistencia> recientes = asistenciaRepository.findByFechaAndHoraAfter(hoy, horaCorte);

        List<String> nombresSociosEnSala = recientes.stream()
                .map(a -> a.getAlumno().getNombre())
                .distinct()
                .toList();

        int totalHoy = asistenciaRepository.findByFecha(hoy).size();

        return new AforoResponseDto(
                nombresSociosEnSala.size(),
                nombresSociosEnSala,
                totalHoy
        );
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> listarTodas() {
        return mapListToDto(asistenciaRepository.findAllOrderByFechaDescHoraDesc());
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> historialPorFecha(LocalDate fecha) {
        return mapListToDto(asistenciaRepository.findByFechaOrderByHoraAsc(fecha));
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> historialPorRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new BusinessException("Debe indicar las fechas 'desde' y 'hasta'.", 400);
        }
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'.", 400);
        }
        return mapListToDto(asistenciaRepository.findByRangoOrderByFechaDescHoraDesc(desde, hasta));
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> historialPorAlumno(Long alumnoId) {
        return mapListToDto(asistenciaRepository.findByAlumnoIdOrderByFechaDesc(alumnoId));
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> distribucionPorHora(LocalDate fecha) {
        return asistenciaRepository.findByFecha(fecha).stream()
                .filter(a -> a.getHora() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getHora().getHour(),
                        Collectors.counting()
                ));
    }

    @Transactional
    public void eliminar(Long id) {
        asistenciaRepository.deleteById(id);
    }

    private List<AsistenciaResponseDto> mapListToDto(List<Asistencia> asistencias) {
        if (asistencias == null || asistencias.isEmpty()) {
            return List.of();
        }

        List<Long> alumnoIds = asistencias.stream()
                .map(Asistencia::getAlumno)
                .filter(Objects::nonNull)
                .map(Alumno::getIdAlumno)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Set<LocalDate>> fechasPorAlumno = new HashMap<>();
        if (!alumnoIds.isEmpty()) {
            LocalDate minFecha = asistencias.stream()
                    .map(Asistencia::getFecha)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());
            LocalDate maxFecha = asistencias.stream()
                    .map(Asistencia::getFecha)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.now());

            LocalDate desdeLunes = inicioSemanaLunes(minFecha);
            LocalDate hastaDomingo = inicioSemanaLunes(maxFecha).plusDays(6);

            List<Asistencia> enRango = asistenciaRepository.findByAlumnoIdsAndRango(alumnoIds, desdeLunes, hastaDomingo);
            for (Asistencia item : enRango) {
                if (item.getAlumno() != null && item.getAlumno().getIdAlumno() != null && item.getFecha() != null) {
                    fechasPorAlumno
                            .computeIfAbsent(item.getAlumno().getIdAlumno(), k -> new HashSet<>())
                            .add(item.getFecha());
                }
            }
        }

        return asistencias.stream()
                .map(a -> mapToDto(a, fechasPorAlumno))
                .toList();
    }

    private AsistenciaResponseDto mapToDto(Asistencia a, Map<Long, Set<LocalDate>> fechasPorAlumno) {
        Alumno al = a.getAlumno();
        Long alumnoId = al != null ? al.getIdAlumno() : null;
        String dni = al != null ? al.getDni() : null;
        Plan plan = al != null ? al.getPlanAlumno() : null;
        String planNombre = plan != null ? plan.getNombre() : "Pase Libre";
        Integer limiteDias = plan != null ? extraerLimiteDiasSemana(plan.getDiaPorSemana()) : null;

        int diasSemana = 1;
        if (alumnoId != null && a.getFecha() != null) {
            LocalDate lunes = inicioSemanaLunes(a.getFecha());
            LocalDate fechaTope = a.getFecha();
            Set<LocalDate> fechas = fechasPorAlumno.getOrDefault(alumnoId, Set.of());
            int cuenta = 0;
            for (LocalDate f : fechas) {
                if (!f.isBefore(lunes) && !f.isAfter(fechaTope)) {
                    cuenta++;
                }
            }
            diasSemana = Math.max(1, cuenta);
        }

        boolean excede = limiteDias != null && diasSemana > limiteDias;

        return new AsistenciaResponseDto(
                a.getId(),
                alumnoId,
                al != null ? al.getNombre() : "Socio",
                dni,
                planNombre,
                a.getFecha(),
                a.getHora(),
                a.getMetodo(),
                a.getEstadoCuenta(),
                diasSemana,
                limiteDias,
                excede
        );
    }
}
