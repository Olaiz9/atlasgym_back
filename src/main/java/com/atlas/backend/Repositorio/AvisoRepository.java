package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvisoRepository extends JpaRepository<Aviso, Long> {

    @Query("SELECT a FROM Aviso a WHERE a.paraTodos = true OR (a.alumno IS NOT NULL AND a.alumno.idAlumno = :idAlumno) ORDER BY a.fecha DESC")
    List<Aviso> findVisiblesParaAlumno(@Param("idAlumno") Long idAlumno);

    List<Aviso> findByParaTodosTrueOrderByFechaDesc();

    List<Aviso> findAllByOrderByFechaDesc();
}
