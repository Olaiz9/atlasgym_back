package com.atlas.backend;

import com.atlas.backend.Modelo.*;
import com.atlas.backend.Seguridad.JwtService;
import com.atlas.backend.Servicio.AlumnoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagoAccesoTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired AlumnoService alumnos;

    private Long alumnoA;
    private Long alumnoB;
    private Long pagoA;
    private Long pagoB;
    private String tokenAlumno;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        Alumno a = crearAlumno("socio-pagos-a");
        Alumno b = crearAlumno("socio-pagos-b");
        alumnoA = a.getIdAlumno();
        alumnoB = b.getIdAlumno();
        pagoA = a.getPagosAlumnos().get(0).getIdPago();
        pagoB = b.getPagosAlumnos().get(0).getIdPago();
        tokenAlumno = token(a.getUsuario(), alumnoA);
        Usuario admin = crearUsuario("admin-pagos", Rol.ADMIN);
        em.persist(admin);
        em.flush();
        tokenAdmin = token(admin, null);
        em.clear();
    }

    @Test
    void alumnoSoloVeSusPagosEnAmbosListados() throws Exception {
        for (String ruta : List.of("/api/pagos", "/api/pagos?mes=2026-06",
                "/api/pagos/alumno/" + alumnoA)) {
            mvc.perform(get(ruta).header("Authorization", tokenAlumno))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(pagoA))
                    .andExpect(jsonPath("$[0].alumnoId").value(alumnoA));
        }
    }

    @Test
    void alumnoNoVePagosAjenosAlCambiarId() throws Exception {
        mvc.perform(get("/api/pagos/alumno/{id}", alumnoB).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$[0]").doesNotExist());
        mvc.perform(get("/api/pagos/{id}", pagoB).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
    }

    @Test
    void idDeAlumnoAjenoInexistenteTambienSeRechaza() throws Exception {
        mvc.perform(get("/api/pagos/alumno/{id}", Long.MAX_VALUE).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorVePagosHistoricosTrasBaja() throws Exception {
        alumnos.darDeBaja(alumnoB);
        em.flush();
        em.clear();
        mvc.perform(get("/api/pagos?mes=2026-06").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/pagos/alumno/{id}", alumnoB).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(pagoB))
                .andExpect(jsonPath("$[0].estado").value("PAGADO"));
        mvc.perform(get("/api/pagos/{id}", pagoB).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("alumnoId").value(alumnoB));
    }

    @Test
    void alumnoNoPuedeCrearModificarNiBorrarPagos() throws Exception {
        long cantidad = em.createQuery("select count(p) from Pago p", Long.class).getSingleResult();
        mvc.perform(post("/api/pagos").header("Authorization", tokenAlumno)
                .contentType(MediaType.APPLICATION_JSON).content(nuevoPago()))
                .andExpect(status().isForbidden());
        for (Long id : List.of(pagoA, pagoB)) {
            mvc.perform(patch("/api/pagos/{id}/estado", id).header("Authorization", tokenAlumno)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PENDIENTE\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/pagos/{id}/estado", id).header("Authorization", tokenAlumno)
                    .param("estado", "PENDIENTE")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/pagos/{id}", id).header("Authorization", tokenAlumno))
                    .andExpect(status().isForbidden());
        }
        em.flush();
        em.clear();
        assertThat(em.createQuery("select count(p) from Pago p", Long.class).getSingleResult()).isEqualTo(cantidad);
        assertThat(em.find(Pago.class, pagoA).getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
        assertThat(em.find(Pago.class, pagoB).getEstadoPago()).isEqualTo(EstadoPago.PAGADO);
    }

    @Test
    void administradorConservaOperacionesDeGestion() throws Exception {
        String respuesta = mvc.perform(post("/api/pagos").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(nuevoPago()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        mvc.perform(patch("/api/pagos/{id}/estado", id).header("Authorization", tokenAdmin)
                .param("estado", "PENDIENTE")).andExpect(status().isOk())
                .andExpect(jsonPath("estado").value("PENDIENTE"));
        mvc.perform(put("/api/pagos/{id}/estado", id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAGADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("estado").value("PAGADO"));
        mvc.perform(delete("/api/pagos/{id}", id).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void sinSesionNoSeConsultanNiGestionanPagos() throws Exception {
        for (String ruta : List.of("/api/pagos", "/api/pagos/alumno/" + alumnoA, "/api/pagos/" + pagoA)) {
            mvc.perform(get(ruta)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/pagos").contentType(MediaType.APPLICATION_JSON).content(nuevoPago()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/pagos/{id}/estado", pagoA).param("estado", "PENDIENTE"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pagos/{id}/estado", pagoA).param("estado", "PENDIENTE"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/pagos/{id}", pagoA)).andExpect(status().isForbidden());
    }

    @Test
    void finanzasIndicaBajaSinModificarImporteNombreNiEstadoDelPago() throws Exception {
        mvc.perform(get("/api/pagos/{id}", pagoB).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alumnoActivo").value(true));
        alumnos.darDeBaja(alumnoB);
        em.flush();
        em.clear();
        for (String ruta : List.of("/api/pagos", "/api/pagos?mes=2026-06",
                "/api/pagos/alumno/" + alumnoB)) {
            String respuesta = mvc.perform(get(ruta).header("Authorization", tokenAdmin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var pagos = json.readTree(respuesta);
            boolean encontrado = false;
            for (var pago : pagos) {
                if (pago.get("id").asLong() == pagoB) {
                    encontrado = true;
                    assertThat(pago.get("alumnoActivo").asBoolean()).isFalse();
                    assertThat(pago.get("alumnoId").asLong()).isEqualTo(alumnoB);
                    assertThat(pago.get("alumnoNombreHistorico").asText()).isEqualTo("socio-pagos-b");
                    assertThat(pago.get("monto").decimalValue()).isEqualByComparingTo("20000");
                    assertThat(pago.get("estado").asText()).isEqualTo("PAGADO");
                }
            }
            assertThat(encontrado).isTrue();
        }
        mvc.perform(get("/api/pagos/{id}", pagoB).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alumnoActivo").value(false));
        mvc.perform(get("/api/pagos/{id}", pagoA).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alumnoActivo").value(true));
    }
    private String nuevoPago() throws Exception {
        return json.writeValueAsString(Map.of("alumnoId", alumnoB, "monto", 1000,
                "metodo", "EFECTIVO", "periodoMes", "2026-07"));
    }

    private String token(Usuario usuario, Long alumnoId) {
        return "Bearer " + jwt.generarToken(usuario.getId(), usuario.getEmail(), usuario.getRol().name(), alumnoId);
    }

    private Usuario crearUsuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(nombre);
        usuario.setPassword("hash-no-utilizable-para-login");
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
    }

    private Alumno crearAlumno(String nombre) {
        Alumno alumno = new Alumno();
        alumno.setNombre(nombre);
        alumno.setActivo(true);
        Usuario usuario = crearUsuario(nombre, Rol.ALUMNO);
        alumno.setUsuario(usuario);
        usuario.setAlumno(alumno);
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("20000"));
        pago.setFecha(LocalDate.of(2026, 6, 5));
        pago.setPeriodoMes("2026-06");
        pago.setAlumnoNombreHistorico(nombre);
        pago.setMetodoPago(MetodoPago.EFECTIVO);
        pago.setEstadoPago(EstadoPago.PAGADO);
        alumno.addPagosAlumnos(pago);
        em.persist(alumno);
        em.flush();
        return alumno;
    }
}
