package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Rutina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RutinaRepository extends JpaRepository<Rutina, Long> {

    List<Rutina> findByEsGenericaTrue();

    List<Rutina> findByEsGenericaFalse();

    @Query("SELECT a.rutinaAlumno FROM Alumno a WHERE a.idAlumno = :alumnoId AND a.rutinaAlumno IS NOT NULL")
    Optional<Rutina> findByAlumnoId(@Param("alumnoId") Long alumnoId);
}
