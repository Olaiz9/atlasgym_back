package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    @Query("SELECT m FROM MovimientoCaja m JOIN FETCH m.pago p LEFT JOIN FETCH p.alumno WHERE m.fecha = :fecha ORDER BY m.id")
    List<MovimientoCaja> findByFechaConPago(@Param("fecha") LocalDate fecha);
}
