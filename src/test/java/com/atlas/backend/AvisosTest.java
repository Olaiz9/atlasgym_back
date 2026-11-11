package com.atlas.backend;

import com.atlas.backend.Dto.AvisoRequestDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.CategoriaAviso;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AvisoRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avisos del gimnasio: comunicados de los titulares para todos los socios o para un socio puntual,
 * lectura por parte del alumno y control de permisos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvisosTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private AvisoRepository avisoRepository;

    private String tokenAdmin;
    private String tokenAlumnoA;
    private String tokenAlumnoB;
    private Alumno alumnoA;
    private Alumno alumnoB;

    @BeforeEach
    void preparar() {
        tokenAdmin = crearTokenTitular("titular.avisos@atlasgym.com");
        alumnoA = crearSocioConUsuario("socio.avisos.a", "41222333", "Socio Avisos A");
        alumnoB = crearSocioConUsuario("socio.avisos.b", "41222334", "Socio Avisos B");
        tokenAlumnoA = tokenParaSocio(alumnoA);
        tokenAlumnoB = tokenParaSocio(alumnoB);
    }

    private String crearTokenTitular(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(Rol.ADMIN);
        u.setActivo(true);
        u = usuarioRepository.saveAndFlush(u);
        return "Bearer " + jwt.generarToken(u.getId(), u.getEmail(), Rol.ADMIN.name(), null);
    }

    private Alumno crearSocioConUsuario(String email, String dni, String nombre) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(Rol.ALUMNO);
        u.setActivo(true);
        u = usuarioRepository.saveAndFlush(u);

        Alumno a = new Alumno();
        a.setNombre(nombre);
        a.setDni(dni);
        a.setEmail(email + "@mail.com");
        a.setActivo(true);
        a.setFechaAlta(LocalDate.now().minusMonths(1));
        a.setUsuario(u);
        return alumnoRepository.saveAndFlush(a);
    }

    private String tokenParaSocio(Alumno alumno) {
        Usuario u = alumno.getUsuario();
        return "Bearer " + jwt.generarToken(u.getId(), u.getEmail(), Rol.ALUMNO.name(), alumno.getIdAlumno());
    }

    @Test
    void titularCreaListaYBorraAvisosYAlumnoLoVeYMarcaComoLeido() throws Exception {
        AvisoRequestDto avisoGeneral = new AvisoRequestDto(
                "Feriado de Carnaval",
                "El lunes abrimos de 9 a 14 hs",
                CategoriaAviso.HORARIO,
                true,
                null
        );

        String respuestaGeneral = mvc.perform(post("/api/avisos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(avisoGeneral)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Feriado de Carnaval"))
                .andExpect(jsonPath("$.paraTodos").value(true))
                .andExpect(jsonPath("$.leido").value(false))
                .andReturn().getResponse().getContentAsString();
        Long idGeneral = json.readTree(respuestaGeneral).get("id").asLong();

        // Aviso privado solo para el socio A
        AvisoRequestDto avisoPrivadoA = new AvisoRequestDto(
                "Certificado medico",
                "Trae el apto fisico actualizado",
                CategoriaAviso.IMPORTANTE,
                false,
                alumnoA.getIdAlumno()
        );
        mvc.perform(post("/api/avisos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(avisoPrivadoA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paraTodos").value(false))
                .andExpect(jsonPath("$.alumnoId").value(alumnoA.getIdAlumno()));

        // El titular lista todos los avisos
        mvc.perform(get("/api/avisos").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Feriado de Carnaval")))
                .andExpect(jsonPath("$[*].titulo", hasItem("Certificado medico")));

        // El socio A ve el aviso general y el suyo privado
        mvc.perform(get("/api/avisos/mis-avisos").header("Authorization", tokenAlumnoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Feriado de Carnaval")))
                .andExpect(jsonPath("$[*].titulo", hasItem("Certificado medico")));

        // El socio B ve el aviso general pero NO el aviso privado del socio A
        mvc.perform(get("/api/avisos/mis-avisos").header("Authorization", tokenAlumnoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Feriado de Carnaval")))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("Certificado medico"))));

        // El socio A marca el aviso general como leido
        mvc.perform(put("/api/avisos/" + idGeneral + "/leido").header("Authorization", tokenAlumnoA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/avisos/mis-avisos").header("Authorization", tokenAlumnoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idGeneral + ")].leido").value(hasItem(true)));

        // El titular borra el aviso general
        mvc.perform(delete("/api/avisos/" + idGeneral).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        assertThat(avisoRepository.findById(idGeneral)).isEmpty();
    }

    @Test
    void alumnoNoPuedeCrearListarTodosNiBorrarAvisosYSinTokenDevuelve401() throws Exception {
        AvisoRequestDto dto = new AvisoRequestDto(
                "Aviso trucho",
                "Hoy gimnasio gratis",
                null,
                true,
                null
        );

        String creado = mvc.perform(post("/api/avisos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long avisoId = json.readTree(creado).get("id").asLong();

        // Alumno no puede crear, listar el panel de admin ni borrar -> 403
        mvc.perform(post("/api/avisos")
                        .header("Authorization", tokenAlumnoA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/avisos").header("Authorization", tokenAlumnoA))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/avisos/" + avisoId).header("Authorization", tokenAlumnoA))
                .andExpect(status().isForbidden());

        // Sin token -> 401
        mvc.perform(get("/api/avisos/mis-avisos"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/avisos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/avisos/" + avisoId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devuelve404SiElAvisoOElAlumnoDestinatarioNoExiste() throws Exception {
        mvc.perform(put("/api/avisos/999999/leido").header("Authorization", tokenAlumnoA))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/avisos/999999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        AvisoRequestDto alumnoInexistente = new AvisoRequestDto(
                "Aviso individual",
                "Mensaje para socio inexistente",
                CategoriaAviso.IMPORTANTE,
                false,
                999999L
        );
        mvc.perform(post("/api/avisos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(alumnoInexistente)))
                .andExpect(status().isNotFound());
    }
}
