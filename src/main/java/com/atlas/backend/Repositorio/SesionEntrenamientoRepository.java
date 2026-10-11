package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.SesionEntrenamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesionEntrenamientoRepository extends JpaRepository<SesionEntrenamiento, Long> {

    @Query("SELECT s FROM SesionEntrenamiento s WHERE s.alumno.idAlumno = :alumnoId ORDER BY s.fecha DESC")
    List<SesionEntrenamiento> findByAlumnoIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId);

    @Query("SELECT s FROM SesionEntrenamiento s WHERE s.alumno.idAlumno = :alumnoId AND s.diaRutina.id = :diaRutinaId ORDER BY s.fecha DESC, s.id DESC")
    List<SesionEntrenamiento> findByAlumnoIdAndDiaRutinaIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId, @Param("diaRutinaId") Long diaRutinaId);

    Optional<SesionEntrenamiento> findFirstByAlumno_IdAlumnoAndDiaRutina_IdOrderByFechaDesc(Long alumnoId, Long diaRutinaId);

    @Query("SELECT s FROM SesionEntrenamiento s WHERE s.alumno.idAlumno = :alumnoId AND s.fecha BETWEEN :desde AND :hasta ORDER BY s.fecha DESC")
    List<SesionEntrenamiento> findByAlumnoIdAndFechaBetween(@Param("alumnoId") Long alumnoId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
