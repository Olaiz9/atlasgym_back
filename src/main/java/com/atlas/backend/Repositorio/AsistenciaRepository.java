package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByClientEventId(String clientEventId);

    List<Asistencia> findByFecha(LocalDate fecha);

    @Query("SELECT a FROM Asistencia a WHERE a.alumno.idAlumno = :alumnoId AND a.fecha = :fecha ORDER BY a.hora DESC")
    List<Asistencia> findByAlumnoIdAndFechaOrderByHoraDesc(@Param("alumnoId") Long alumnoId, @Param("fecha") LocalDate fecha);

    @Query("SELECT DISTINCT a.fecha FROM Asistencia a WHERE a.alumno.idAlumno = :alumnoId AND a.fecha BETWEEN :desde AND :hasta")
    List<LocalDate> findFechasDistintasByAlumnoIdAndRango(
            @Param("alumnoId") Long alumnoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    @Query("SELECT a FROM Asistencia a WHERE a.alumno.idAlumno IN :alumnoIds AND a.fecha BETWEEN :desde AND :hasta")
    List<Asistencia> findByAlumnoIdsAndRango(
            @Param("alumnoIds") List<Long> alumnoIds,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    List<Asistencia> findByFechaAndHoraAfter(LocalDate fecha, LocalTime horaCorte);

    @Query("SELECT a FROM Asistencia a LEFT JOIN FETCH a.alumno al LEFT JOIN FETCH al.planAlumno WHERE a.fecha = :fecha ORDER BY a.hora ASC")
    List<Asistencia> findByFechaOrderByHoraAsc(@Param("fecha") LocalDate fecha);

    @Query("SELECT a FROM Asistencia a LEFT JOIN FETCH a.alumno al LEFT JOIN FETCH al.planAlumno WHERE a.alumno.idAlumno = :alumnoId ORDER BY a.fecha DESC, a.hora DESC")
    List<Asistencia> findByAlumnoIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId);

    @Query("SELECT a FROM Asistencia a LEFT JOIN FETCH a.alumno al LEFT JOIN FETCH al.planAlumno WHERE a.fecha BETWEEN :desde AND :hasta ORDER BY a.fecha DESC, a.hora DESC")
    List<Asistencia> findByRangoOrderByFechaDescHoraDesc(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    @Query("SELECT a FROM Asistencia a LEFT JOIN FETCH a.alumno al LEFT JOIN FETCH al.planAlumno ORDER BY a.fecha DESC, a.hora DESC")
    List<Asistencia> findAllOrderByFechaDescHoraDesc();
}
