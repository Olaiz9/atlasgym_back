package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.VideoTecnicaDto;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Modelo.VideoTecnica;
import com.atlas.backend.Repositorio.VideoTecnicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoTecnicaService {

    private final VideoTecnicaRepository videoTecnicaRepository;

    public VideoTecnicaService(VideoTecnicaRepository videoTecnicaRepository) {
        this.videoTecnicaRepository = videoTecnicaRepository;
    }

    @Transactional(readOnly = true)
    public List<VideoTecnicaDto> listarTodos() {
        return videoTecnicaRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VideoTecnicaDto> listarPorGrupo(GrupoMuscular grupo) {
        return videoTecnicaRepository.findByGrupoMuscularVideo(grupo).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public VideoTecnicaDto buscarPorId(Long id) {
        VideoTecnica v = videoTecnicaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VideoTecnica", id));
        return mapToDto(v);
    }

    @Transactional(readOnly = true)
    public List<VideoTecnicaDto> buscarPorTitulo(String query) {
        return videoTecnicaRepository.findByTituloContainingIgnoreCase(query).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public VideoTecnicaDto crear(VideoTecnicaDto dto) {
        VideoTecnica v = new VideoTecnica();
        v.setTitulo(dto.titulo());
        v.setDescripcion(dto.descripcion());
        v.setGrupoMuscularVideo(dto.grupoMuscularVideo());
        v.setNivel(dto.nivel());
        v.setDuracion(dto.duracion());
        v.setUrlVideo(dto.urlVideo());
        v.setThumbnailUrl(dto.thumbnailUrl());
        v.setGifUrl(dto.gifUrl());
        v.setFormato(dto.formato());
        v.setEquipo(dto.equipo());
        if (dto.consejosClave() != null) {
            v.getConsejosClave().addAll(dto.consejosClave());
        }

        VideoTecnica guardado = videoTecnicaRepository.save(v);
        return mapToDto(guardado);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!videoTecnicaRepository.existsById(id)) {
            throw new ResourceNotFoundException("VideoTecnica", id);
        }
        videoTecnicaRepository.deleteById(id);
    }

    private VideoTecnicaDto mapToDto(VideoTecnica v) {
        return new VideoTecnicaDto(
                v.getId(),
                v.getTitulo(),
                v.getDescripcion(),
                v.getGrupoMuscularVideo(),
                v.getNivel(),
                v.getDuracion(),
                v.getUrlVideo(),
                v.getThumbnailUrl(),
                v.getGifUrl(),
                v.getFormato(),
                v.getEquipo(),
                v.getConsejosClave()
        );
    }
}
