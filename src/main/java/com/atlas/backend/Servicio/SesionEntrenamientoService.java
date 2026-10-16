package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.GuardarSesionRequestDto;
import com.atlas.backend.Dto.RegistroSerieDto;
import com.atlas.backend.Dto.SesionEjercicioDto;
import com.atlas.backend.Dto.SesionResponseDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.DiaRutina;
import com.atlas.backend.Modelo.Ejercicio;
import com.atlas.backend.Modelo.RegistroSerie;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.SesionEjercicio;
import com.atlas.backend.Modelo.SesionEntrenamiento;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.DiaRutinaRepository;
import com.atlas.backend.Repositorio.EjercicioRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.SesionEntrenamientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SesionEntrenamientoService {

    private final SesionEntrenamientoRepository sesionRepository;
    private final AlumnoRepository alumnoRepository;
    private final RutinaRepository rutinaRepository;
    private final DiaRutinaRepository diaRutinaRepository;
    private final EjercicioRepository ejercicioRepository;

    public SesionEntrenamientoService(SesionEntrenamientoRepository sesionRepository,
                                      AlumnoRepository alumnoRepository,
                                      RutinaRepository rutinaRepository,
                                      DiaRutinaRepository diaRutinaRepository,
                                      EjercicioRepository ejercicioRepository) {
        this.sesionRepository = sesionRepository;
        this.alumnoRepository = alumnoRepository;
        this.rutinaRepository = rutinaRepository;
        this.diaRutinaRepository = diaRutinaRepository;
        this.ejercicioRepository = ejercicioRepository;
    }

    /**
     * Guarda la sesión completa finalizada por el alumno desde el Workout Tracker del frontend.
     */
    @Transactional
    public SesionResponseDto guardarSesion(GuardarSesionRequestDto dto) {
        Alumno alumno = alumnoRepository.findById(dto.alumnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", dto.alumnoId()));

        Rutina rutina = null;
        if (dto.rutinaId() != null) {
            rutina = rutinaRepository.findById(dto.rutinaId()).orElse(null);
        }

        DiaRutina diaRutina = null;
        if (dto.diaRutinaId() != null) {
            diaRutina = diaRutinaRepository.findById(dto.diaRutinaId()).orElse(null);
        }

        SesionEntrenamiento sesion = new SesionEntrenamiento();
        sesion.setAlumno(alumno);
        sesion.setRutina(rutina);
        sesion.setDiaRutina(diaRutina);
        sesion.setFecha(dto.fecha() != null ? dto.fecha() : LocalDate.now());
        sesion.setDuracionMinutos(dto.duracionMinutos());
        sesion.setNotas(dto.notas());

        if (dto.ejercicios() != null) {
            for (SesionEjercicioDto seDto : dto.ejercicios()) {
                SesionEjercicio se = new SesionEjercicio();
                se.setSesionEntrenamiento(sesion);
                se.setNombreEjercicioHistorico(seDto.nombreEjercicio());

                if (seDto.ejercicioId() != null) {
                    ejercicioRepository.findById(seDto.ejercicioId()).ifPresent(se::setEjercicio);
                }

                if (seDto.series() != null) {
                    for (RegistroSerieDto rsDto : seDto.series()) {
                        RegistroSerie rs = new RegistroSerie();
                        rs.setSesionEjercicio(se);
                        rs.setSerieNumero(rsDto.serieNumero());
                        rs.setKg(rsDto.kg());
                        rs.setReps(rsDto.reps() != null ? rsDto.reps() : 0);
                        rs.setCompletada(rsDto.completada() != null ? rsDto.completada() : true);
                        se.getSeries().add(rs);
                    }
                }

                sesion.getEjercicios().add(se);
            }
        }

        SesionEntrenamiento guardada = sesionRepository.save(sesion);
        return mapToDto(guardada);
    }

    /**
     * Trae la sesión más reciente realizada por el alumno para el mismo día de rutina.
     * Permite mostrar en la UI: "La última vez levantaste X kg".
     */
    @Transactional(readOnly = true)
    public Optional<SesionResponseDto> obtenerUltimaSesion(Long alumnoId, Long diaRutinaId) {
        return sesionRepository
                .findFirstByAlumno_IdAlumnoAndDiaRutina_IdOrderByFechaDesc(alumnoId, diaRutinaId)
                .map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public List<SesionResponseDto> historialPorAlumno(Long alumnoId) {
        return sesionRepository.findByAlumnoIdOrderByFechaDesc(alumnoId).stream()
                .map(this::mapToDto)
                .toList();
    }

    private SesionResponseDto mapToDto(SesionEntrenamiento s) {
        String diaNombre = s.getDiaRutina() != null ? s.getDiaRutina().getNombre() : null;
        Long diaId = s.getDiaRutina() != null ? s.getDiaRutina().getId() : null;
        Long rutinaId = s.getRutina() != null ? s.getRutina().getId() : null;

        List<SesionEjercicioDto> ejsDto = new ArrayList<>();
        if (s.getEjercicios() != null) {
            for (SesionEjercicio se : s.getEjercicios()) {
                Long ejId = se.getEjercicio() != null ? se.getEjercicio().getId() : null;
                String ejNombre = se.getNombreEjercicioHistorico() != null ?
                        se.getNombreEjercicioHistorico() :
                        (se.getEjercicio() != null ? se.getEjercicio().getNombreEjercicio() : "Ejercicio");

                List<RegistroSerieDto> seriesDto = new ArrayList<>();
                if (se.getSeries() != null) {
                    for (RegistroSerie rs : se.getSeries()) {
                        seriesDto.add(new RegistroSerieDto(
                                rs.getId(),
                                rs.getSerieNumero(),
                                rs.getKg(),
                                rs.getReps(),
                                rs.isCompletada()
                        ));
                    }
                }

                ejsDto.add(new SesionEjercicioDto(
                        se.getId(),
                        ejId,
                        ejNombre,
                        seriesDto
                ));
            }
        }

        return new SesionResponseDto(
                s.getId(),
                s.getAlumno().getIdAlumno(),
                rutinaId,
                diaId,
                diaNombre,
                s.getFecha(),
                s.getDuracionMinutos(),
                s.getNotas(),
                ejsDto
        );
    }
}
