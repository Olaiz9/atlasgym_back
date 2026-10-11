package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Modelo.VideoTecnica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoTecnicaRepository extends JpaRepository<VideoTecnica, Long> {

    List<VideoTecnica> findByGrupoMuscularVideo(GrupoMuscular grupoMuscularVideo);

    List<VideoTecnica> findByTituloContainingIgnoreCase(String titulo);
}
