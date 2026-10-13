package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AforoResponseDto;
import com.atlas.backend.Dto.AsistenciaResponseDto;
import com.atlas.backend.Dto.TotemCheckInResponseDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.MetodoAsistencia;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AsistenciaService {

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
     * Valida el ingreso de un socio en el Kiosco Tótem por DNI.
     * Regla Anti-fraude: Si el alumno ya ingresó hoy y transcurrieron menos de 100 minutos,
     * rechaza la entrada duplicada con "Tu sesión continúa activa."
     */
    @Transactional
    public TotemCheckInResponseDto checkInPorDni(String dni) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByDni(dni);

        if (alumnoOpt.isEmpty()) {
            return new TotemCheckInResponseDto(
                    false,
                    false,
                    "DNI no encontrado en el sistema.",
                    null,
                    null,
                    null,
                    dni,
                    null,
                    null,
                    null
            );
        }

        Alumno alumno = alumnoOpt.get();
        String planNombre = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getNombre() : "Pase Libre";

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
                    null
            );
        }

        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        List<Asistencia> asistenciasHoy = asistenciaRepository
                .findByAlumnoIdAndFechaOrderByHoraDesc(alumno.getIdAlumno(), hoy);

        if (!asistenciasHoy.isEmpty()) {
            Asistencia ultima = asistenciasHoy.get(0);
            if (ultima.getHora() != null) {
                long minutosTranscurridos = Duration.between(ultima.getHora(), ahora).toMinutes();
                if (minutosTranscurridos >= 0 && minutosTranscurridos < 100) {
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
                            ultima.getHora()
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
        Asistencia guardada = asistenciaRepository.save(nuevaAsistencia);

        alumno.setUltimaAsistencia(hoy);
        alumnoRepository.save(alumno);

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
                ahora
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
        return asistenciaRepository.findAllOrderByFechaDescHoraDesc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> historialPorFecha(LocalDate fecha) {
        return asistenciaRepository.findByFechaOrderByHoraAsc(fecha).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AsistenciaResponseDto> historialPorAlumno(Long alumnoId) {
        return asistenciaRepository.findByAlumnoIdOrderByFechaDesc(alumnoId).stream()
                .map(this::mapToDto)
                .toList();
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

    private AsistenciaResponseDto mapToDto(Asistencia a) {
        Alumno al = a.getAlumno();
        String dni = al != null ? al.getDni() : null;
        String planNombre = (al != null && al.getPlanAlumno() != null)
                ? al.getPlanAlumno().getNombre()
                : "Pase Libre";

        return new AsistenciaResponseDto(
                a.getId(),
                al != null ? al.getIdAlumno() : null,
                al != null ? al.getNombre() : "Socio",
                dni,
                planNombre,
                a.getFecha(),
                a.getHora(),
                a.getMetodo(),
                a.getEstadoCuenta()
        );
    }
}
