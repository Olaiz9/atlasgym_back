package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    List<Asistencia> findByFecha(LocalDate fecha);

    @Query("SELECT a FROM Asistencia a WHERE a.alumno.idAlumno = :alumnoId AND a.fecha = :fecha ORDER BY a.hora DESC")
    List<Asistencia> findByAlumnoIdAndFechaOrderByHoraDesc(@Param("alumnoId") Long alumnoId, @Param("fecha") LocalDate fecha);

    List<Asistencia> findByFechaAndHoraAfter(LocalDate fecha, LocalTime horaCorte);

    @Query("SELECT a FROM Asistencia a WHERE a.fecha = :fecha ORDER BY a.hora ASC")
    List<Asistencia> findByFechaOrderByHoraAsc(@Param("fecha") LocalDate fecha);

    @Query("SELECT a FROM Asistencia a WHERE a.alumno.idAlumno = :alumnoId ORDER BY a.fecha DESC, a.hora DESC")
    List<Asistencia> findByAlumnoIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId);
}
