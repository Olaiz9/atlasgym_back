package com.atlas.backend;

import com.atlas.backend.Dto.VideoTecnicaDto;
import com.atlas.backend.Modelo.FormatoVideo;
import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Repositorio.VideoTecnicaRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Videoteca de tecnica: catalogo de videos y GIFs explicativos por grupo muscular.
 * Nota de seguridad: los endpoints GET de /api/videoteca son publicos a proposito en SecurityConfig
 * para permitir consultar la tecnica de los ejercicios sin autenticacion; crear y borrar son exclusivos
 * de los titulares (ROLE_ADMIN).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VideotecaTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private VideoTecnicaRepository videoTecnicaRepository;

    private String tokenAdmin;
    private String tokenAlumno;

    @BeforeEach
    void preparar() {
        tokenAdmin = token("titular.videoteca@atlasgym.com", Rol.ADMIN);
        tokenAlumno = token("socio.videoteca", Rol.ALUMNO);
    }

    private String token(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(rol);
        u.setActivo(true);
        u = usuarioRepository.saveAndFlush(u);
        return "Bearer " + jwt.generarToken(u.getId(), u.getEmail(), rol.name(), null);
    }

    private VideoTecnicaDto nuevoVideo(String titulo, GrupoMuscular grupo) {
        return new VideoTecnicaDto(
                null,
                titulo,
                "Ejecucion controlada cuidando la postura escapular",
                grupo,
                "Intermedio",
                "0:45",
                "https://cdn.atlasgym.com/videos/press.mp4",
                "https://cdn.atlasgym.com/thumbs/press.jpg",
                "https://cdn.atlasgym.com/gifs/press.gif",
                FormatoVideo.VIDEO,
                "Barra olimpica y banco plano",
                List.of("Retraer escapulas", "Apoyar bien los pies")
        );
    }

    @Test
    void titularCreaYBorraVideosYLosGetFuncionanComoCatalogoPublico() throws Exception {
        String respuestaPecho = mvc.perform(post("/api/videoteca")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nuevoVideo("Press Plano con Barra", GrupoMuscular.PECHO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Press Plano con Barra"))
                .andExpect(jsonPath("$.grupoMuscularVideo").value("PECHO"))
                .andReturn().getResponse().getContentAsString();
        Long idPecho = json.readTree(respuestaPecho).get("id").asLong();

        mvc.perform(post("/api/videoteca")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nuevoVideo("Sentadilla Libre", GrupoMuscular.PIERNAS))))
                .andExpect(status().isCreated());

        // Los GET de videoteca son publicos a proposito (SecurityConfig): se prueban sin token
        mvc.perform(get("/api/videoteca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Press Plano con Barra")))
                .andExpect(jsonPath("$[*].titulo", hasItem("Sentadilla Libre")));

        mvc.perform(get("/api/videoteca/grupo/PECHO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Press Plano con Barra")))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("Sentadilla Libre"))));

        mvc.perform(get("/api/videoteca/buscar").param("query", "sentadilla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItem("Sentadilla Libre")))
                .andExpect(jsonPath("$[*].titulo", not(hasItem("Press Plano con Barra"))));

        mvc.perform(get("/api/videoteca/" + idPecho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Press Plano con Barra"))
                .andExpect(jsonPath("$.consejosClave", hasItem("Retraer escapulas")));

        // El titular elimina el video
        mvc.perform(delete("/api/videoteca/" + idPecho).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        assertThat(videoTecnicaRepository.findById(idPecho)).isEmpty();
        mvc.perform(get("/api/videoteca/" + idPecho))
                .andExpect(status().isNotFound());
    }

    @Test
    void alumnoNoPuedeCrearNiBorrarVideosYSinTokenDevuelve401() throws Exception {
        String creado = mvc.perform(post("/api/videoteca")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nuevoVideo("Dominadas Pronas", GrupoMuscular.ESPALDA))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idVideo = json.readTree(creado).get("id").asLong();

        // Alumno recibe 403 al intentar crear o borrar
        mvc.perform(post("/api/videoteca")
                        .header("Authorization", tokenAlumno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nuevoVideo("Video no autorizado", GrupoMuscular.BRAZOS))))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/videoteca/" + idVideo).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());

        // Sin token recibe 401 al intentar crear o borrar
        mvc.perform(post("/api/videoteca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nuevoVideo("Video sin token", GrupoMuscular.CORE))))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/videoteca/" + idVideo))
                .andExpect(status().isUnauthorized());

        // Eliminar un video inexistente como titular devuelve 404
        mvc.perform(delete("/api/videoteca/999999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }
}
