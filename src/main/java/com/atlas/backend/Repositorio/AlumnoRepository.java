package com.atlas.backend.Repositorio;

import com.atlas.backend.Modelo.Alumno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    Optional<Alumno> findByDni(String dni);

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno WHERE a.dni = :dni")
    Optional<Alumno> findByDniWithRelaciones(@Param("dni") String dni);

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno WHERE a.idAlumno = :id")
    Optional<Alumno> findByIdWithRelaciones(@Param("id") Long id);

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno")
    List<Alumno> findAllWithRelaciones();

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno WHERE a.activo = true")
    List<Alumno> findActivosWithRelaciones();

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno WHERE a.activo = false")
    List<Alumno> findInactivosWithRelaciones();

    @Query("SELECT a FROM Alumno a LEFT JOIN FETCH a.usuario LEFT JOIN FETCH a.planAlumno LEFT JOIN FETCH a.rutinaAlumno WHERE LOWER(a.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Alumno> findByNombreContainingIgnoreCaseWithRelaciones(@Param("nombre") String nombre);

    Optional<Alumno> findByEmailIgnoreCase(String email);

    List<Alumno> findByActivoTrue();

    List<Alumno> findByActivoFalse();

    List<Alumno> findByNombreContainingIgnoreCase(String nombre);

    boolean existsByDni(String dni);

    boolean existsByEmail(String email);

    @Query("SELECT a FROM Alumno a WHERE a.planAlumno.idPlan = :idPlan")
    List<Alumno> findByPlanId(@Param("idPlan") Long idPlan);

    @Query("SELECT a FROM Alumno a WHERE a.usuario.id = :usuarioId")
    Optional<Alumno> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Alumno a SET a.rutinaAlumno = null WHERE a.rutinaAlumno.id = :rutinaId")
    void desvincularRutina(@Param("rutinaId") Long rutinaId);
}
