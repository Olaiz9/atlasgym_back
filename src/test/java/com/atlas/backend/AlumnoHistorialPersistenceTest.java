package com.atlas.backend;

import com.atlas.backend.Modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class AlumnoHistorialPersistenceTest {
    @Autowired
    private TestEntityManager em;

    @Test
    void bajaEnJulioConservaPagoDeJunioYTodoElHistorial() {
        Alumno alumno = crearAlumnoConHistorial();
        Long id = alumno.getIdAlumno();
        Long usuarioId = alumno.getUsuario().getId();

        alumno.darDeBaja();
        alumno.darDeBaja(); // Repetir la baja no borra ni duplica informacion.
        em.flush();
        em.clear();

        Alumno guardado = em.find(Alumno.class, id);
        assertThat(guardado.isActivo()).isFalse();
        assertThat(guardado.getAsistencias()).hasSize(1);
        assertThat(guardado.getSesionesEntrenamiento()).hasSize(1);
        assertThat(em.find(Usuario.class, usuarioId)).isNotNull();

        // Finanzas consulta los pagos del periodo sin filtrar alumnos activos.
        var pagosJunio = em.getEntityManager().createQuery(
                "select p from Pago p where p.periodoMes = :periodo", Pago.class)
                .setParameter("periodo", "2026-06").getResultList();
        assertThat(pagosJunio).hasSize(1);
        Pago pago = pagosJunio.get(0);
        assertThat(pago.getMonto()).isEqualByComparingTo("20000.00");
        assertThat(pago.getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
        assertThat(pago.getAlumno().getIdAlumno()).isEqualTo(id);
        assertThat(pago.getAlumnoNombreHistorico()).isEqualTo("Juan Perez");
        assertThat(guardado.getPagosAlumnos()).hasSize(1);
    }

    @Test
    void noPermiteBorrarFisicamenteUnAlumnoConJpa() {
        Alumno alumno = crearAlumnoConHistorial();
        assertThatThrownBy(() -> em.remove(alumno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("darDeBaja()");
    }

    @Test
    void quitarDeColeccionesInversasNoEliminaRegistrosHistoricos() {
        Alumno alumno = crearAlumnoConHistorial();
        Long id = alumno.getIdAlumno();
        alumno.getPagosAlumnos().clear();
        alumno.getAsistencias().clear();
        alumno.getSesionesEntrenamiento().clear();
        em.flush();
        em.clear();

        Alumno recargado = em.find(Alumno.class, id);
        assertThat(recargado.getPagosAlumnos()).hasSize(1);
        assertThat(recargado.getAsistencias()).hasSize(1);
        assertThat(recargado.getSesionesEntrenamiento()).hasSize(1);
    }

    private Alumno crearAlumnoConHistorial() {
        Alumno alumno = new Alumno();
        alumno.setNombre("Juan");
        alumno.setApellido("Perez");
        alumno.setDni("12345678");
        alumno.setActivo(true);
        alumno.setFechaAlta(LocalDate.of(2026, 6, 1));

        Usuario usuario = new Usuario();
        usuario.setEmail("juan@example.test");
        usuario.setPassword("hash-de-prueba-no-utilizable");
        usuario.setRol(Rol.ALUMNO);
        usuario.setActivo(true);
        alumno.setUsuario(usuario);
        usuario.setAlumno(alumno);

        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("20000.00"));
        pago.setFecha(LocalDate.of(2026, 6, 5));
        pago.setPeriodoMes("2026-06");
        pago.setAlumnoNombreHistorico("Juan Perez");
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setEstadoPago(EstadoPago.PAGADO);
        alumno.addPagosAlumnos(pago);

        Asistencia asistencia = new Asistencia();
        asistencia.setFecha(LocalDate.of(2026, 6, 10));
        alumno.addAsistencias(asistencia);

        SesionEntrenamiento sesion = new SesionEntrenamiento();
        sesion.setFecha(LocalDate.of(2026, 6, 10));
        alumno.addSesionEntrenamiento(sesion);

        em.persistAndFlush(alumno);
        return alumno;
    }
}
