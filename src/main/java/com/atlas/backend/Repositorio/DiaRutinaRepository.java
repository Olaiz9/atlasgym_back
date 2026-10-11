package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.DiaRutina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiaRutinaRepository extends JpaRepository<DiaRutina, Long> {

    @Query("SELECT d FROM DiaRutina d WHERE d.rutinaDia.id = :rutinaId ORDER BY d.orden ASC")
    List<DiaRutina> findByRutinaIdOrderByOrdenAsc(@Param("rutinaId") Long rutinaId);
}
