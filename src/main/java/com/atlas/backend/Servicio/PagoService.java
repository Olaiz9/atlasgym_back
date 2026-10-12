package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.MetodoPago;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;

    public PagoService(PagoRepository pagoRepository,
                       AlumnoRepository alumnoRepository,
                       PlanRepository planRepository) {
        this.pagoRepository = pagoRepository;
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
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

        Pago pago = new Pago();
        pago.setAlumno(alumno);
        pago.setPlanPago(plan);
        pago.setMonto(dto.monto());
        pago.setFecha(LocalDate.now());
        pago.setPeriodoMes(dto.periodoMes());
        // Snapshot inmutable: preserva el nombre real del alumno al momento del cobro
        pago.setAlumnoNombreHistorico(alumno.getNombre());
        pago.setMetodoPago(metodo);
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago.setComprobante(dto.comprobante());

        Pago guardado = pagoRepository.save(pago);
        return mapToDto(guardado);
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
    public PagoResponseDto buscarPorId(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
        return mapToDto(pago);
    }

    private MetodoPago parseMetodoPago(String metodoStr) {
        if (metodoStr == null) return MetodoPago.EFECTIVO;
        try {
            return MetodoPago.valueOf(metodoStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return MetodoPago.EFECTIVO;
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
                p.getComprobante()
        );
    }
}
