package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AvisoRequestDto;
import com.atlas.backend.Dto.AvisoResponseDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Aviso;
import com.atlas.backend.Modelo.CategoriaAviso;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AvisoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AvisoService {

    private final AvisoRepository avisoRepository;
    private final AlumnoRepository alumnoRepository;

    public AvisoService(AvisoRepository avisoRepository, AlumnoRepository alumnoRepository) {
        this.avisoRepository = avisoRepository;
        this.alumnoRepository = alumnoRepository;
    }

    @Transactional(readOnly = true)
    public List<AvisoResponseDto> listarParaAlumno(Long alumnoId, Long usuarioId) {
        return avisoRepository.findVisiblesParaAlumno(alumnoId).stream()
                .map(a -> mapToDto(a, usuarioId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvisoResponseDto> listarTodos(Long usuarioId) {
        return avisoRepository.findAllByOrderByFechaDesc().stream()
                .map(a -> mapToDto(a, usuarioId))
                .toList();
    }

    @Transactional
    public AvisoResponseDto crear(AvisoRequestDto dto) {
        Aviso aviso = new Aviso();
        aviso.setTitulo(dto.titulo());
        aviso.setMensaje(dto.mensaje());
        aviso.setCategoria(dto.categoria() != null ? dto.categoria() : CategoriaAviso.NOVEDAD);
        aviso.setFecha(LocalDate.now());
        aviso.setParaTodos(dto.paraTodos() != null ? dto.paraTodos() : true);

        if (Boolean.FALSE.equals(dto.paraTodos()) && dto.alumnoId() != null) {
            Alumno alumno = alumnoRepository.findById(dto.alumnoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alumno", dto.alumnoId()));
            aviso.setAlumno(alumno);
        }

        Aviso guardado = avisoRepository.save(aviso);
        return mapToDto(guardado, null);
    }

    @Transactional
    public void marcarLeido(Long avisoId, Long usuarioId) {
        Aviso aviso = avisoRepository.findById(avisoId)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso", avisoId));

        aviso.getLeidoPor().add(usuarioId);
        avisoRepository.save(aviso);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!avisoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Aviso", id);
        }
        avisoRepository.deleteById(id);
    }

    private AvisoResponseDto mapToDto(Aviso a, Long usuarioId) {
        boolean leido = usuarioId != null && a.getLeidoPor().contains(usuarioId);
        String nombreAlumno = a.getAlumno() != null ? a.getAlumno().getNombre() : null;
        Long alumnoId = a.getAlumno() != null ? a.getAlumno().getIdAlumno() : null;

        return new AvisoResponseDto(
                a.getId(),
                a.getTitulo(),
                a.getMensaje(),
                a.getCategoria(),
                a.getFecha(),
                a.isParaTodos(),
                alumnoId,
                nombreAlumno,
                leido
        );
    }
}
