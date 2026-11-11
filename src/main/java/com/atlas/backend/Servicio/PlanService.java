package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.PlanRequestDto;
import com.atlas.backend.Dto.PlanResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final AlumnoRepository alumnoRepository;
    private final PagoRepository pagoRepository;

    public PlanService(PlanRepository planRepository, AlumnoRepository alumnoRepository,
                       PagoRepository pagoRepository) {
        this.planRepository = planRepository;
        this.alumnoRepository = alumnoRepository;
        this.pagoRepository = pagoRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponseDto> listarActivos() {
        return planRepository.findByActivoTrue().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanResponseDto> listarTodos() {
        return planRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponseDto buscarPorId(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));
        return mapToDto(plan);
    }

    @Transactional
    public PlanResponseDto crear(PlanRequestDto dto) {
        Plan plan = new Plan();
        plan.setNombre(dto.nombre());
        plan.setPrecio(dto.precio());
        plan.setDescripcion(dto.descripcion());
        plan.setDiaPorSemana(dto.diaPorSemana());
        plan.setActivo(dto.activo() != null ? dto.activo() : true);

        Plan guardado = planRepository.save(plan);
        return mapToDto(guardado);
    }

    @Transactional
    public PlanResponseDto actualizar(Long id, PlanRequestDto dto) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));

        plan.setNombre(dto.nombre());
        plan.setPrecio(dto.precio());
        plan.setDescripcion(dto.descripcion());
        plan.setDiaPorSemana(dto.diaPorSemana());
        if (dto.activo() != null) {
            plan.setActivo(dto.activo());
        }

        Plan actualizado = planRepository.save(plan);
        return mapToDto(actualizado);
    }

    /**
     * Elimina un plan desde la pantalla de Planes.
     * - Con socios activos: no se puede (409); primero hay que cambiarles el plan.
     * - Con historial (cobros registrados o socios dados de baja con ese plan): se DESACTIVA en vez
     *   de borrarse. Borrarlo dejaria esos cobros como "Sin plan" (la FK es ON DELETE SET NULL)
     *   y a los ex socios sin plan si se los reactiva. Deja de ofrecerse igual que si se borrara.
     * - Sin ningun uso (por ejemplo, cargado por error): se borra definitivamente.
     */
    @Transactional
    public void eliminar(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));

        List<Alumno> alumnosConPlan = alumnoRepository.findByPlanId(id);
        List<Alumno> alumnosActivos = alumnosConPlan.stream()
                .filter(Alumno::isActivo)
                .toList();

        if (!alumnosActivos.isEmpty()) {
            throw new BusinessException(
                    "No se puede eliminar el plan: tiene " + alumnosActivos.size() +
                            " alumno(s) activo(s) asignado(s).",
                    409
            );
        }

        boolean tieneHistorial = !alumnosConPlan.isEmpty() || pagoRepository.existsByPlanId(id);
        if (tieneHistorial) {
            plan.setActivo(false);
            planRepository.save(plan);
            return;
        }

        planRepository.delete(plan);
    }

    private PlanResponseDto mapToDto(Plan p) {
        return new PlanResponseDto(
                p.getIdPlan(),
                p.getNombre(),
                p.getPrecio(),
                p.getDescripcion(),
                p.getDiaPorSemana(),
                p.isActivo()
        );
    }
}
