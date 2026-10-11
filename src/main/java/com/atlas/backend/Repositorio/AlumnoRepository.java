package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Alumno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    Optional<Alumno> findByDni(String dni);

    List<Alumno> findByActivoTrue();

    List<Alumno> findByActivoFalse();

    List<Alumno> findByNombreContainingIgnoreCase(String nombre);

    boolean existsByDni(String dni);

    boolean existsByEmail(String email);

    @Query("SELECT a FROM Alumno a WHERE a.planAlumno.idPlan = :idPlan")
    List<Alumno> findByPlanId(@Param("idPlan") Long idPlan);

    @Query("SELECT a FROM Alumno a WHERE a.usuario.id = :usuarioId")
    Optional<Alumno> findByUsuarioId(@Param("usuarioId") Long usuarioId);
}
