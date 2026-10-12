package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.DiaRutinaDto;
import com.atlas.backend.Dto.EjercicioDto;
import com.atlas.backend.Dto.RutinaRequestDto;
import com.atlas.backend.Dto.RutinaResponseDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.DiaRutina;
import com.atlas.backend.Modelo.Ejercicio;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.TipoSerieEjercicio;
import com.atlas.backend.Modelo.VideoTecnica;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.VideoTecnicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RutinaService {

    private final RutinaRepository rutinaRepository;
    private final AlumnoRepository alumnoRepository;
    private final VideoTecnicaRepository videoTecnicaRepository;

    public RutinaService(RutinaRepository rutinaRepository,
                         AlumnoRepository alumnoRepository,
                         VideoTecnicaRepository videoTecnicaRepository) {
        this.rutinaRepository = rutinaRepository;
        this.alumnoRepository = alumnoRepository;
        this.videoTecnicaRepository = videoTecnicaRepository;
    }

    @Transactional(readOnly = true)
    public List<RutinaResponseDto> listarGenericas() {
        return rutinaRepository.findByEsGenericaTrue().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RutinaResponseDto> listarTodas() {
        return rutinaRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RutinaResponseDto buscarPorId(Long id) {
        Rutina rutina = rutinaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rutina", id));
        return mapToDto(rutina);
    }

    @Transactional(readOnly = true)
    public RutinaResponseDto obtenerRutinaDeAlumno(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", alumnoId));

        if (alumno.getRutinaAlumno() == null) {
            throw new ResourceNotFoundException("El alumno no tiene ninguna rutina asignada.");
        }

        return mapToDto(alumno.getRutinaAlumno());
    }

    @Transactional
    public RutinaResponseDto crear(RutinaRequestDto dto) {
        Rutina rutina = new Rutina();
        rutina.setNombre(dto.nombre());
        rutina.setDescripcion(dto.descripcion());
        rutina.setObjetivo(dto.objetivo());
        rutina.setEsGenerica(dto.esGenerica() != null ? dto.esGenerica() : false);
        rutina.setFechaCreacion(LocalDate.now());

        if (dto.dias() != null) {
            for (DiaRutinaDto diaDto : dto.dias()) {
                DiaRutina dia = construirDiaEntidad(diaDto);
                rutina.addDiasRutina(dia);
            }
        }

        Rutina guardada = rutinaRepository.save(rutina);
        return mapToDto(guardada);
    }

    /**
     * Actualiza una rutina y propaga automáticamente los cambios a todos los alumnos
     * que la tienen asignada (ya que referencian la rutina por clave foránea).
     */
    @Transactional
    public RutinaResponseDto actualizar(Long id, RutinaRequestDto dto) {
        Rutina rutina = rutinaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rutina", id));

        rutina.setNombre(dto.nombre());
        rutina.setDescripcion(dto.descripcion());
        rutina.setObjetivo(dto.objetivo());
        if (dto.esGenerica() != null) {
            rutina.setEsGenerica(dto.esGenerica());
        }

        // Limpiar días anteriores y reconstruir (orphanRemoval = true limpia los registros hijos)
        rutina.getDiasRutina().clear();

        if (dto.dias() != null) {
            for (DiaRutinaDto diaDto : dto.dias()) {
                DiaRutina dia = construirDiaEntidad(diaDto);
                rutina.addDiasRutina(dia);
            }
        }

        Rutina actualizada = rutinaRepository.save(rutina);
        return mapToDto(actualizada);
    }

    @Transactional
    public void asignarAAlumno(Long rutinaId, Long alumnoId) {
        Rutina rutina = rutinaRepository.findById(rutinaId)
                .orElseThrow(() -> new ResourceNotFoundException("Rutina", rutinaId));
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", alumnoId));

        alumno.setRutinaAlumno(rutina);
        alumnoRepository.save(alumno);
    }

    @Transactional
    public void desasignarDeAlumno(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", alumnoId));

        alumno.setRutinaAlumno(null);
        alumnoRepository.save(alumno);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!rutinaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Rutina", id);
        }
        rutinaRepository.deleteById(id);
    }

    private DiaRutina construirDiaEntidad(DiaRutinaDto diaDto) {
        DiaRutina dia = new DiaRutina();
        dia.setNombre(diaDto.nombre());
        dia.setOrden(diaDto.orden());

        if (diaDto.ejercicios() != null) {
            for (EjercicioDto ejDto : diaDto.ejercicios()) {
                Ejercicio ej = new Ejercicio();
                ej.setNombreEjercicio(ejDto.nombreEjercicio());
                ej.setSerie(ejDto.serie());
                ej.setRepeticiones(ejDto.repeticiones());
                ej.setDescansoSegundos(ejDto.descansoSegundos());
                ej.setNotas(ejDto.notas());
                ej.setOrden(ejDto.orden());
                ej.setTipoSerie(ejDto.tipoSerie() != null ? ejDto.tipoSerie() : TipoSerieEjercicio.NORMAL);

                if (ejDto.videoTecnicaId() != null) {
                    videoTecnicaRepository.findById(ejDto.videoTecnicaId())
                            .ifPresent(ej::setVideoTecnica);
                }

                dia.addEjercicio(ej);
            }
        }

        if (diaDto.videoIds() != null) {
            for (Long vId : diaDto.videoIds()) {
                videoTecnicaRepository.findById(vId).ifPresent(dia::addVideo);
            }
        }

        return dia;
    }

    public RutinaResponseDto mapToDto(Rutina r) {
        List<DiaRutinaDto> diasDto = new ArrayList<>();

        if (r.getDiasRutina() != null) {
            for (DiaRutina d : r.getDiasRutina()) {
                List<EjercicioDto> ejsDto = new ArrayList<>();
                if (d.getEjercicios() != null) {
                    for (Ejercicio e : d.getEjercicios()) {
                        Long videoId = e.getVideoTecnica() != null ? e.getVideoTecnica().getId() : null;
                        String videoTitulo = e.getVideoTecnica() != null ? e.getVideoTecnica().getTitulo() : null;
                        ejsDto.add(new EjercicioDto(
                                e.getId(),
                                e.getNombreEjercicio(),
                                e.getSerie(),
                                e.getRepeticiones(),
                                e.getDescansoSegundos(),
                                e.getNotas(),
                                e.getOrden(),
                                e.getTipoSerie(),
                                videoId,
                                videoTitulo
                        ));
                    }
                }

                List<Long> videoIds = d.getVideos() != null ?
                        d.getVideos().stream().map(VideoTecnica::getId).toList() : List.of();

                diasDto.add(new DiaRutinaDto(
                        d.getId(),
                        d.getNombre(),
                        d.getOrden(),
                        ejsDto,
                        videoIds
                ));
            }
        }

        return new RutinaResponseDto(
                r.getId(),
                r.getNombre(),
                r.getDescripcion(),
                r.getObjetivo(),
                r.isEsGenerica(),
                diasDto
        );
    }
}
