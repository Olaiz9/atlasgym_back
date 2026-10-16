package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("SELECT p FROM Pago p WHERE p.alumno.idAlumno = :alumnoId ORDER BY p.fecha DESC")
    List<Pago> findByAlumnoIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId);

    List<Pago> findByPeriodoMes(String periodoMes);

    @Query("SELECT p FROM Pago p WHERE p.alumno.idAlumno = :alumnoId AND p.periodoMes = :periodoMes ORDER BY p.fecha DESC")
    List<Pago> findByAlumnoIdAndPeriodoMesOrderByFechaDesc(@Param("alumnoId") Long alumnoId, @Param("periodoMes") String periodoMes);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Pago p WHERE p.alumno.idAlumno = :alumnoId AND p.periodoMes = :periodoMes")
    boolean existsByAlumnoIdAndPeriodoMes(@Param("alumnoId") Long alumnoId, @Param("periodoMes") String periodoMes);
}
