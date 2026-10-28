package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.MetodoPago;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final AlumnoService alumnoService;

    public PagoService(PagoRepository pagoRepository,
                       AlumnoRepository alumnoRepository,
                       PlanRepository planRepository,
                       AsistenciaRepository asistenciaRepository,
                       AlumnoService alumnoService) {
        this.pagoRepository = pagoRepository;
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.alumnoService = alumnoService;
    }

    @Transactional
    public PagoResponseDto registrarPago(PagoRequestDto dto) {
        Alumno alumno = alumnoRepository.findById(dto.alumnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", dto.alumnoId()));

        Plan plan = null;
        if (dto.planId() != null) {
            plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
        } else if (alumno.getPlanAlumno() != null) {
            plan = alumno.getPlanAlumno();
        }

        MetodoPago metodo = parseMetodoPago(dto.metodo());
        String periodoMes = (dto.periodoMes() != null && !dto.periodoMes().isBlank())
                ? dto.periodoMes()
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        Pago pago = new Pago();
        pago.setAlumno(alumno);
        pago.setPlanPago(plan);
        pago.setMonto(dto.monto());
        pago.setFecha(LocalDate.now());
        pago.setPeriodoMes(periodoMes);
        // Snapshot inmutable: preserva el nombre real del alumno al momento del cobro
        pago.setAlumnoNombreHistorico(alumno.getNombre());
        pago.setMetodoPago(metodo);
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setComprobante(dto.comprobante());

        Pago guardado = pagoRepository.saveAndFlush(pago);
        if (dto.usuarioApp() != null && !dto.usuarioApp().isBlank()
                && dto.passwordApp() != null && !dto.passwordApp().isBlank()) {
            alumnoService.asignarCredenciales(alumno.getIdAlumno(), dto.usuarioApp(), dto.passwordApp());
        }
        sincronizarAsistenciaHoy(alumno);
        return mapToDto(guardado);
    }

    @Transactional
    public PagoResponseDto actualizarEstado(Long id, String nuevoEstadoStr) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));

        EstadoPago nuevoEstado = parseEstadoPago(nuevoEstadoStr);
        pago.setEstadoPago(nuevoEstado);
        Pago actualizado = pagoRepository.saveAndFlush(pago);

        if (pago.getAlumno() != null) {
            sincronizarAsistenciaHoy(pago.getAlumno());
        }

        return mapToDto(actualizado);
    }

    @Transactional
    public void eliminar(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
        Alumno alumno = pago.getAlumno();
        pagoRepository.delete(pago);
        pagoRepository.flush();

        if (alumno != null) {
            sincronizarAsistenciaHoy(alumno);
        }
    }

    private void sincronizarAsistenciaHoy(Alumno alumno) {
        EstadoCuenta nuevoEstadoCuenta = alumnoService.calcularEstadoCuenta(alumno);
        List<Asistencia> asistenciasHoy = asistenciaRepository
                .findByAlumnoIdAndFechaOrderByHoraDesc(alumno.getIdAlumno(), LocalDate.now());
        for (Asistencia a : asistenciasHoy) {
            a.setEstadoCuenta(nuevoEstadoCuenta);
            asistenciaRepository.save(a);
        }
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarPorAlumno(Long alumnoId) {
        return pagoRepository.findByAlumnoIdOrderByFechaDesc(alumnoId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarPorMes(String periodoMes) {
        return pagoRepository.findByPeriodoMes(periodoMes).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PagoResponseDto> listarTodos() {
        return pagoRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagoResponseDto buscarPorId(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
        return mapToDto(pago);
    }

    private MetodoPago parseMetodoPago(String metodoStr) {
        if (metodoStr == null) return MetodoPago.EFECTIVO;
        String clean = metodoStr.toUpperCase().trim();
        if (clean.contains("TRANSF") || clean.contains("MERCADO")) return MetodoPago.TRANSFERENCIA;
        if (clean.contains("TARJ") || clean.contains("DEBIT") || clean.contains("CREDIT")) return MetodoPago.TARJETA;
        try {
            return MetodoPago.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return MetodoPago.EFECTIVO;
        }
    }

    private EstadoPago parseEstadoPago(String estadoStr) {
        if (estadoStr == null) return EstadoPago.PAGADO;
        String clean = estadoStr.toUpperCase().trim();
        if (clean.startsWith("PEND")) return EstadoPago.PENDIENTE;
        if (clean.startsWith("VENC") || clean.startsWith("MOROS")) return EstadoPago.VENCIDO;
        try {
            return EstadoPago.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return EstadoPago.PAGADO;
        }
    }

    private PagoResponseDto mapToDto(Pago p) {
        String planNombre = p.getPlanPago() != null ? p.getPlanPago().getNombre() : "Sin plan";
        String alumnoNombre = p.getAlumnoNombreHistorico() != null ?
                p.getAlumnoNombreHistorico() :
                (p.getAlumno() != null ? p.getAlumno().getNombre() : "Desconocido");

        return new PagoResponseDto(
                p.getIdPago(),
                p.getAlumno() != null ? p.getAlumno().getIdAlumno() : null,
                alumnoNombre,
                planNombre,
                p.getMonto(),
                p.getFecha(),
                p.getMetodoPago() != null ? p.getMetodoPago().name() : MetodoPago.EFECTIVO.name(),
                p.getEstadoPago() != null ? p.getEstadoPago().name() : EstadoPago.PAGADO.name(),
                p.getPeriodoMes(),
                p.getComprobante(),
                p.getAlumno() != null ? p.getAlumno().isActivo() : false
        );
    }
}
