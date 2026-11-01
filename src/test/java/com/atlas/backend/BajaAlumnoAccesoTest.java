package com.atlas.backend;

import com.atlas.backend.Modelo.*;
import com.atlas.backend.Servicio.AlumnoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BajaAlumnoAccesoTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired AlumnoService alumnos;

    @Test
    void bajaImpideLoginYTokenAnteriorPeroConservaPago() throws Exception {
        Alumno alumno = crearAlumno();
        Long id = alumno.getIdAlumno();
        Long usuarioId = alumno.getUsuario().getId();
        String token = iniciarSesion("socio-prueba");
        mvc.perform(get("/api/alumnos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        alumnos.darDeBaja(id);
        em.flush();
        em.clear();

        assertThat(em.find(Alumno.class, id).isActivo()).isFalse();
        assertThat(em.find(Usuario.class, usuarioId).isActivo()).isFalse();
        assertThat(em.find(Alumno.class, id).getPagosAlumnos()).singleElement()
                .satisfies(pago -> {
                    assertThat(pago.getMonto()).isEqualByComparingTo("20000");
                    assertThat(pago.getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
                });
        verificarBloqueo(id, token);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credenciales("31234567"))).andExpect(status().isForbidden());
    }

    @Test
    void alumnoInactivoNoAccedeAunqueSuUsuarioHayaQuedadoActivo() throws Exception {
        Alumno alumno = crearAlumno();
        String token = iniciarSesion("socio-prueba");
        Long id = alumno.getIdAlumno();
        alumno.darDeBaja();
        em.flush();
        em.clear();
        verificarBloqueo(id, token);
    }

    @Test
    void usuarioInactivoNoAccedeAunqueAlumnoEsteActivo() throws Exception {
        Alumno alumno = crearAlumno();
        String token = iniciarSesion("socio-prueba");
        Long id = alumno.getIdAlumno();
        alumno.getUsuario().setActivo(false);
        em.flush();
        em.clear();
        verificarBloqueo(id, token);
    }

    @Test
    void administradorActivoSinFichaSigueAccediendo() throws Exception {
        Usuario admin = crearUsuario("admin-prueba", Rol.ADMIN);
        em.persist(admin);
        em.flush();
        em.clear();
        String token = iniciarSesion("admin-prueba");
        mvc.perform(get("/api/alumnos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void bajaPorApiConservaHistorialYListadosAunqueSeRepita() throws Exception {
        Alumno alumno = crearAlumno();
        Long id = alumno.getIdAlumno();
        Long usuarioId = alumno.getUsuario().getId();
        Asistencia asistencia = new Asistencia();
        asistencia.setFecha(LocalDate.of(2026, 6, 10));
        alumno.addAsistencias(asistencia);
        SesionEntrenamiento sesion = new SesionEntrenamiento();
        sesion.setFecha(LocalDate.of(2026, 6, 10));
        SesionEjercicio ejercicio = new SesionEjercicio(null, "Sentadilla");
        ejercicio.addSerie(new RegistroSerie(1, 40.0, 10, true));
        sesion.addEjercicio(ejercicio);
        alumno.addSesionEntrenamiento(sesion);
        em.persist(crearUsuario("admin-prueba", Rol.ADMIN));
        em.flush();
        String tokenAlumno = iniciarSesion("socio-prueba");
        String tokenAdmin = iniciarSesion("admin-prueba");
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(delete("/api/alumnos/{id}", id)
                    .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isNoContent());
            em.flush();
            em.clear();
        }
        Alumno conservado = em.find(Alumno.class, id);
        assertThat(conservado.isActivo()).isFalse();
        assertThat(em.find(Usuario.class, usuarioId).isActivo()).isFalse();
        assertThat(conservado.getAsistencias()).singleElement()
                .satisfies(a -> assertThat(a.getFecha()).isEqualTo(LocalDate.of(2026, 6, 10)));
        assertThat(conservado.getSesionesEntrenamiento()).singleElement()
                .satisfies(s -> assertThat(s.getEjercicios()).singleElement()
                        .satisfies(e -> assertThat(e.getSeries()).singleElement()
                                .satisfies(serie -> {
                                    assertThat(serie.getKg()).isEqualTo(40.0);
                                    assertThat(serie.getReps()).isEqualTo(10);
                                    assertThat(serie.isCompletada()).isTrue();
                                })));
        mvc.perform(get("/api/alumnos").param("soloActivos", "true")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/alumnos/inactivos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/alumnos/{id}", id).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/api/pagos").param("mes", "2026-06")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].monto").value(20000));
        verificarBloqueo(id, tokenAlumno);
    }

    @Test
    void bajaSinCuentaConservaFicha() throws Exception {
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio sin cuenta");
        alumno.setDni("39876543");
        alumno.setActivo(true);
        em.persist(alumno);
        em.persist(crearUsuario("admin-prueba", Rol.ADMIN));
        em.flush();
        Long id = alumno.getIdAlumno();
        String token = iniciarSesion("admin-prueba");
        mvc.perform(delete("/api/alumnos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();
        assertThat(em.find(Alumno.class, id).isActivo()).isFalse();
        assertThat(em.find(Alumno.class, id).getUsuario()).isNull();
    }

    @Test
    void alumnoNoPuedeEjecutarSuPropiaBaja() throws Exception {
        Alumno alumno = crearAlumno();
        Long id = alumno.getIdAlumno();
        String token = iniciarSesion("socio-prueba");
        mvc.perform(delete("/api/alumnos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        em.flush();
        em.clear();
        assertThat(em.find(Alumno.class, id).isActivo()).isTrue();
    }
    private void verificarBloqueo(Long id, String token) throws Exception {
        mvc.perform(get("/api/alumnos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credenciales("socio-prueba"))).andExpect(status().isForbidden());
    }

    private String iniciarSesion(String usuario) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private String credenciales(String usuario) throws Exception {
        return json.writeValueAsString(Map.of("email", usuario, "password", "clave-prueba"));
    }

    private Usuario crearUsuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(nombre);
        usuario.setPassword(encoder.encode("clave-prueba"));
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private Alumno crearAlumno() {
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio prueba");
        alumno.setDni("31234567");
        alumno.setActivo(true);
        Usuario usuario = crearUsuario("socio-prueba", Rol.ALUMNO);
        alumno.setUsuario(usuario);
        usuario.setAlumno(alumno);
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("20000"));
        pago.setFecha(LocalDate.of(2026, 6, 5));
        pago.setPeriodoMes("2026-06");
        pago.setAlumnoNombreHistorico("Socio prueba");
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setEstadoPago(EstadoPago.PAGADO);
        alumno.addPagosAlumnos(pago);
        em.persist(alumno);
        em.flush();
        return alumno;
    }
}
