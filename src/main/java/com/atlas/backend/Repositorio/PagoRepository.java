package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("SELECT p FROM Pago p LEFT JOIN FETCH p.planPago LEFT JOIN FETCH p.alumno WHERE p.alumno.idAlumno = :alumnoId ORDER BY p.fecha DESC, p.idPago DESC")
    List<Pago> findByAlumnoIdOrderByFechaDesc(@Param("alumnoId") Long alumnoId);

    @Query("SELECT p FROM Pago p WHERE p.alumno.idAlumno IN :alumnoIds ORDER BY p.fecha DESC, p.idPago DESC")
    List<Pago> findByAlumnoIdsOrderByFechaDesc(@Param("alumnoIds") List<Long> alumnoIds);

    @Query("SELECT p FROM Pago p LEFT JOIN FETCH p.alumno LEFT JOIN FETCH p.planPago ORDER BY p.fecha DESC, p.idPago DESC")
    List<Pago> findAllWithRelacionesOrderByFechaDesc();

    @Query("SELECT p FROM Pago p LEFT JOIN FETCH p.alumno LEFT JOIN FETCH p.planPago WHERE p.periodoMes = :periodoMes ORDER BY p.fecha DESC, p.idPago DESC")
    List<Pago> findByPeriodoMesWithRelaciones(@Param("periodoMes") String periodoMes);

    @Query("SELECT p FROM Pago p LEFT JOIN FETCH p.alumno LEFT JOIN FETCH p.planPago WHERE p.fecha = :fecha ORDER BY p.idPago DESC")
    List<Pago> findByFechaWithRelaciones(@Param("fecha") LocalDate fecha);

    @Query("SELECT p FROM Pago p LEFT JOIN FETCH p.alumno LEFT JOIN FETCH p.planPago WHERE p.estadoPago = com.atlas.backend.Modelo.EstadoPago.ANULADO AND p.fechaAnulacion >= :desde AND p.fechaAnulacion < :hasta ORDER BY p.fechaAnulacion DESC")
    List<Pago> findAnuladosEnVentana(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<Pago> findByPeriodoMes(String periodoMes);

    @Query("SELECT p FROM Pago p WHERE p.alumno.idAlumno = :alumnoId AND p.periodoMes = :periodoMes ORDER BY p.fecha DESC")
    List<Pago> findByAlumnoIdAndPeriodoMesOrderByFechaDesc(@Param("alumnoId") Long alumnoId, @Param("periodoMes") String periodoMes);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Pago p WHERE p.alumno.idAlumno = :alumnoId AND p.periodoMes = :periodoMes")
    boolean existsByAlumnoIdAndPeriodoMes(@Param("alumnoId") Long alumnoId, @Param("periodoMes") String periodoMes);
}
