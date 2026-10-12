package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AlumnoRequestDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final RutinaRepository rutinaRepository;
    private final PagoRepository pagoRepository;

    public AlumnoService(AlumnoRepository alumnoRepository,
                         PlanRepository planRepository,
                         RutinaRepository rutinaRepository,
                         PagoRepository pagoRepository) {
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.rutinaRepository = rutinaRepository;
        this.pagoRepository = pagoRepository;
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarActivos() {
        return alumnoRepository.findByActivoTrue().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarInactivos() {
        return alumnoRepository.findByActivoFalse().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorId(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorDni(String dni) {
        Alumno alumno = alumnoRepository.findByDni(dni)
                .orElseThrow(() -> new BusinessException("No se encontró ningún alumno con el DNI: " + dni, 404));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> buscarPorNombre(String query) {
        return alumnoRepository.findByNombreContainingIgnoreCase(query).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public AlumnoResponseDto crear(AlumnoRequestDto dto) {
        if (alumnoRepository.existsByDni(dto.dni())) {
            throw new BusinessException("El DNI " + dto.dni() + " ya pertenece a un socio registrado.", 409);
        }

        if (dto.email() != null && !dto.email().isBlank() && alumnoRepository.existsByEmail(dto.email())) {
            throw new BusinessException("El email " + dto.email() + " ya se encuentra registrado.", 409);
        }

        Alumno alumno = new Alumno();
        alumno.setNombre(dto.nombre());
        alumno.setDni(dto.dni());
        alumno.setEmail(dto.email());
        alumno.setCelular(dto.celular());
        alumno.setFechaAlta(LocalDate.now());
        alumno.setActivo(dto.activo() != null ? dto.activo() : true);

        if (dto.planId() != null) {
            Plan plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
            alumno.setPlanAlumno(plan);
        }

        if (dto.rutinaId() != null) {
            Rutina rutina = rutinaRepository.findById(dto.rutinaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rutina", dto.rutinaId()));
            alumno.setRutinaAlumno(rutina);
        }

        Alumno guardado = alumnoRepository.save(alumno);
        return mapToDto(guardado);
    }

    @Transactional
    public AlumnoResponseDto actualizar(Long id, AlumnoRequestDto dto) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));

        if (!alumno.getDni().equals(dto.dni()) && alumnoRepository.existsByDni(dto.dni())) {
            throw new BusinessException("El nuevo DNI " + dto.dni() + " ya está en uso por otro socio.", 409);
        }

        if (dto.email() != null && !dto.email().equals(alumno.getEmail()) && alumnoRepository.existsByEmail(dto.email())) {
            throw new BusinessException("El nuevo email " + dto.email() + " ya está en uso.", 409);
        }

        alumno.setNombre(dto.nombre());
        alumno.setDni(dto.dni());
        alumno.setEmail(dto.email());
        alumno.setCelular(dto.celular());

        if (dto.activo() != null) {
            alumno.setActivo(dto.activo());
        }

        if (dto.planId() != null) {
            Plan plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
            alumno.setPlanAlumno(plan);
        } else {
            alumno.setPlanAlumno(null);
        }

        if (dto.rutinaId() != null) {
            Rutina rutina = rutinaRepository.findById(dto.rutinaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rutina", dto.rutinaId()));
            alumno.setRutinaAlumno(rutina);
        } else {
            alumno.setRutinaAlumno(null);
        }

        Alumno actualizado = alumnoRepository.save(alumno);
        return mapToDto(actualizado);
    }

    @Transactional
    public void darDeBaja(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        alumno.darDeBaja();
        alumnoRepository.save(alumno);
    }

    /**
     * Calcula dinámicamente el estado de cuenta del alumno en base a su actividad y pagos.
     * Reglas de ATLAS Gym:
     * - Si activo == false -> INACTIVO.
     * - Si último pago tiene más de 60 días -> INACTIVO.
     * - Si pagó el mes en curso (YYYY-MM) -> AL_DIA.
     * - Si no pagó pero estamos en los primeros 10 días del mes (período de gracia) -> PENDIENTE.
     * - Si no pagó y pasaron más de 10 días -> MOROSO.
     */
    public EstadoCuenta calcularEstadoCuenta(Alumno alumno) {
        if (!alumno.isActivo()) {
            return EstadoCuenta.INACTIVO;
        }

        String periodoActual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        boolean pagoMesActual = pagoRepository.existsByAlumnoIdAndPeriodoMes(alumno.getIdAlumno(), periodoActual);

        if (pagoMesActual) {
            return EstadoCuenta.AL_DIA;
        }

        List<Pago> historial = pagoRepository.findByAlumnoIdOrderByFechaDesc(alumno.getIdAlumno());
        if (!historial.isEmpty()) {
            Pago ultimoPago = historial.get(0);
            if (ultimoPago.getFecha().isBefore(LocalDate.now().minusDays(60))) {
                return EstadoCuenta.INACTIVO;
            }
        } else if (alumno.getFechaAlta() != null && alumno.getFechaAlta().isBefore(LocalDate.now().minusDays(60))) {
            return EstadoCuenta.INACTIVO;
        }

        if (LocalDate.now().getDayOfMonth() <= 10) {
            return EstadoCuenta.PENDIENTE;
        }

        return EstadoCuenta.MOROSO;
    }

    public AlumnoResponseDto mapToDto(Alumno alumno) {
        Long planId = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getIdPlan() : null;
        String planNombre = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getNombre() : null;

        Long rutinaId = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getId() : null;
        String rutinaNombre = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getNombre() : null;

        EstadoCuenta estadoCuenta = calcularEstadoCuenta(alumno);

        return new AlumnoResponseDto(
                alumno.getIdAlumno(),
                alumno.getNombre(),
                alumno.getDni(),
                alumno.getEmail(),
                alumno.getCelular(),
                planId,
                planNombre,
                alumno.getFechaAlta(),
                alumno.isActivo(),
                estadoCuenta,
                rutinaId,
                rutinaNombre
        );
    }
}
