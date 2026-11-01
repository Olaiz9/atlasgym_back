package com.atlas.backend;

import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/alumnos/{id}/credenciales (ramaBruno) no debe poder apropiarse de la cuenta
 * de un titular: antes, si el usuario escrito coincidia con un ADMIN, lo convertia en
 * ALUMNO y le cambiaba la clave.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CredencialesAlumnoTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    @Test
    void noConvierteLaCuentaDeUnTitularEnAlumno() throws Exception {
        Usuario titular1 = crearAdmin("titular1@prueba.local");
        Usuario titular2 = crearAdmin("titular2@prueba.local");
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio sin cuenta");
        alumno.setDni("39111222");
        alumno.setActivo(true);
        em.persist(alumno);
        em.flush();
        String token = iniciarSesion("titular1@prueba.local");

        mvc.perform(post("/api/alumnos/{id}/credenciales", alumno.getIdAlumno())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "usuario", "TITULAR2@prueba.local", "password", "39111222"))))
                .andExpect(status().isConflict());

        em.flush();
        em.clear();
        Usuario intacto = em.find(Usuario.class, titular2.getId());
        assertThat(intacto.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(encoder.matches("clave-titular-prueba", intacto.getPassword())).isTrue();
        assertThat(em.find(Alumno.class, alumno.getIdAlumno()).getUsuario()).isNull();
        assertThat(titular1.getId()).isNotNull();
    }

    /**
     * La clave que elige el titular en Finanzas (boton "Generala") la conoce el titular:
     * el socio tiene que reemplazarla por una propia en su primer ingreso.
     */
    @Test
    void laClaveAsignadaPorElTitularObligaAlSocioACambiarla() throws Exception {
        crearAdmin("titular3@prueba.local");
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio con clave del titular");
        alumno.setDni("39111333");
        alumno.setActivo(true);
        em.persist(alumno);
        em.flush();
        String token = iniciarSesion("titular3@prueba.local");

        mvc.perform(post("/api/alumnos/{id}/credenciales", alumno.getIdAlumno())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "usuario", "socio.clave.titular", "password", "Kx7mP2qa"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "socio.clave.titular", "password", "Kx7mP2qa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true));
    }

    private Usuario crearAdmin(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword(encoder.encode("clave-titular-prueba"));
        u.setRol(Rol.ADMIN);
        u.setActivo(true);
        em.persist(u);
        return u;
    }

    private String iniciarSesion(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "clave-titular-prueba"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }
}
