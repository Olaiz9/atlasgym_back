package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Ejercicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EjercicioRepository extends JpaRepository<Ejercicio, Long> {

    @Query("SELECT e FROM Ejercicio e WHERE e.diaRutina.id = :diaRutinaId ORDER BY e.orden ASC")
    List<Ejercicio> findByDiaRutinaIdOrderByOrdenAsc(@Param("diaRutinaId") Long diaRutinaId);
}
