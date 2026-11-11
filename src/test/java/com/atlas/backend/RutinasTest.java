package com.atlas.backend;

import com.atlas.backend.Dto.DiaRutinaDto;
import com.atlas.backend.Dto.EjercicioDto;
import com.atlas.backend.Dto.RutinaRequestDto;
import com.atlas.backend.Modelo.*;
import com.atlas.backend.Seguridad.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integracion de rutinas y permisos: Issue #1. Usa H2 y JWT reales de prueba. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RutinasTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private EntityManager em;

    private String tokenAdmin;
    private String tokenAlumno;
    private Long alumnoId;
    private Long otroAlumnoId;

    @BeforeEach
    void preparar() {
        Usuario admin = usuario("titular.rutinas@example.test", Rol.ADMIN);
        Alumno alumno = socio("30100101", usuario("socio.rutinas", Rol.ALUMNO));
        Alumno otro = socio("30100102", usuario("otro.rutinas", Rol.ALUMNO));
        em.flush();
        alumnoId = alumno.getIdAlumno();
        otroAlumnoId = otro.getIdAlumno();
        tokenAdmin = token(admin, null);
        tokenAlumno = token(alumno.getUsuario(), alumnoId);
        em.clear();
    }

    @Test
    void titularCreaEditaListaYBorraRutinaConDiasYEjercicios() throws Exception {
        Long id = crear("Fuerza inicial", false);
        JsonNode original = consultar(id, tokenAdmin);
        long diaId = original.at("/dias/0/id").asLong();
        long ejercicioId = original.at("/dias/0/ejercicios/0/id").asLong();
        assertThat(diaId).isPositive();
        assertThat(ejercicioId).isPositive();
        assertThat(original.at("/dias/0/ejercicios/0/nombreEjercicio").asText()).isEqualTo("Sentadilla");

        actualizar(id, cuerpo("Fuerza editada", false,
                List.of(dia("Piernas", 1, List.of(ejercicio("Prensa", 5, null))))));
        JsonNode editada = consultar(id, tokenAdmin);
        assertThat(editada.get("nombre").asText()).isEqualTo("Fuerza editada");
        assertThat(editada.get("descripcion").asText()).isEqualTo("Plan de prueba");
        assertThat(editada.get("objetivo").asText()).isEqualTo("FUERZA");
        assertThat(editada.at("/dias/0/id").asLong()).isEqualTo(diaId);
        assertThat(editada.at("/dias/0/nombre").asText()).isEqualTo("Piernas");
        assertThat(editada.at("/dias/0/ejercicios/0/id").asLong()).isEqualTo(ejercicioId);
        assertThat(editada.at("/dias/0/ejercicios/0/nombreEjercicio").asText()).isEqualTo("Prensa");
        assertThat(editada.at("/dias/0/ejercicios/0/serie").asInt()).isEqualTo(5);
        mvc.perform(get("/api/rutinas").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].nombre", hasItem("Fuerza editada")));
        mvc.perform(delete("/api/rutinas/{id}", id).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        recargar();
        assertThat(em.find(Rutina.class, id)).isNull();
        assertThat(em.find(DiaRutina.class, diaId)).isNull();
        assertThat(em.find(Ejercicio.class, ejercicioId)).isNull();
        mvc.perform(get("/api/rutinas/{id}", id).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void titularAgregaYQuitaDiasYEjercicios() throws Exception {
        Long id = crear("Progresion", false);
        long diaOriginal = consultar(id, tokenAdmin).at("/dias/0/id").asLong();
        actualizar(id, cuerpo("Progresion", false, List.of(
                dia("Dia 1", 1, List.of(ejercicio("Sentadilla", 3, null), ejercicio("Prensa", 4, null))),
                dia("Dia 2", 2, List.of(ejercicio("Remo", 3, null))))));
        JsonNode ampliada = consultar(id, tokenAdmin);
        assertThat(ampliada.get("dias").size()).isEqualTo(2);
        assertThat(ampliada.at("/dias/0/ejercicios").size()).isEqualTo(2);
        long ejercicioQuitado = ampliada.at("/dias/0/ejercicios/1/id").asLong();
        long diaQuitado = ampliada.at("/dias/1/id").asLong();
        long ejercicioDelDiaQuitado = ampliada.at("/dias/1/ejercicios/0/id").asLong();
        actualizar(id, cuerpo("Progresion", false,
                List.of(dia("Dia 1", 1, List.of(ejercicio("Sentadilla", 3, null))))));
        JsonNode reducida = consultar(id, tokenAdmin);
        assertThat(reducida.get("dias").size()).isEqualTo(1);
        assertThat(reducida.at("/dias/0/id").asLong()).isEqualTo(diaOriginal);
        assertThat(reducida.at("/dias/0/ejercicios").size()).isEqualTo(1);
        assertThat(em.find(Ejercicio.class, ejercicioQuitado)).isNull();
        assertThat(em.find(DiaRutina.class, diaQuitado)).isNull();
        assertThat(em.find(Ejercicio.class, ejercicioDelDiaQuitado)).isNull();
    }

    @Test
    void titularAsignaYDesasignaYAlumnoConsultaSuRutina() throws Exception {
        Long id = crear("Personal", false);
        asignar(id, alumnoId);
        assertThat(em.find(Alumno.class, alumnoId).getRutinaAlumno().getId()).isEqualTo(id);
        for (String ruta : List.of("/api/rutinas/mis-rutinas", "/api/rutinas/alumno/" + alumnoId,
                "/api/rutinas/" + id)) {
            mvc.perform(get(ruta).header("Authorization", tokenAlumno))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        }
        mvc.perform(get("/api/rutinas/alumno/{id}", alumnoId).header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete("/api/rutinas/asignar/{id}", alumnoId).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        recargar();
        assertThat(em.find(Alumno.class, alumnoId).getRutinaAlumno()).isNull();
        assertThat(em.find(Rutina.class, id)).isNotNull();
        verificarSinRutina();
    }

    @Test
    void alumnoSinRutinaRecibe204SinCuerpo() throws Exception {
        verificarSinRutina();
    }

    @Test
    void genericasNoIncluyeRutinasPrivadasYAlumnoPuedeVerUnaPlantilla() throws Exception {
        Long generica = crear("Plantilla publica", true);
        crear("Plan privado", false);
        for (String token : List.of(tokenAdmin, tokenAlumno)) {
            mvc.perform(get("/api/rutinas/genericas").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].nombre", hasItem("Plantilla publica")))
                    .andExpect(jsonPath("$[*].nombre", not(hasItem("Plan privado"))));
        }
        assertThat(consultar(generica, tokenAlumno).get("esGenerica").asBoolean()).isTrue();
    }

    @Test
    void alumnoNoVeRutinaDeOtroNiListadoAdministrativo() throws Exception {
        Long propia = crear("Propia", false);
        Long ajena = crear("Ajena", false);
        asignar(propia, alumnoId);
        asignar(ajena, otroAlumnoId);
        for (String ruta : List.of("/api/rutinas/alumno/" + otroAlumnoId, "/api/rutinas/" + ajena,
                "/api/rutinas")) {
            mvc.perform(get(ruta).header("Authorization", tokenAlumno)).andExpect(status().isForbidden());
        }
        assertThat(consultar(propia, tokenAlumno).get("id").asLong()).isEqualTo(propia);
    }

    @Test
    void alumnoSinRutinaTampocoPuedeConsultarUnaPrivadaAjena() throws Exception {
        Long ajena = crear("Ajena", false);
        asignar(ajena, otroAlumnoId);
        mvc.perform(get("/api/rutinas/{id}", ajena).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
    }

    @Test
    void alumnoNoPuedeCrearEditarAsignarDesasignarNiBorrar() throws Exception {
        Long id = crear("Protegida", false);
        asignar(id, alumnoId);
        for (MockHttpServletRequestBuilder peticion : escrituras(id)) {
            mvc.perform(peticion.header("Authorization", tokenAlumno)).andExpect(status().isForbidden());
        }
        recargar();
        assertThat(em.find(Rutina.class, id).getNombre()).isEqualTo("Protegida");
        assertThat(em.find(Alumno.class, alumnoId).getRutinaAlumno().getId()).isEqualTo(id);
        assertThat(em.find(Alumno.class, otroAlumnoId).getRutinaAlumno()).isNull();
        mvc.perform(get("/api/rutinas").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sinTokenTodasLasOperacionesResponden401() throws Exception {
        Long id = crear("Protegida", false);
        for (String ruta : List.of("/api/rutinas", "/api/rutinas/genericas", "/api/rutinas/" + id,
                "/api/rutinas/mis-rutinas", "/api/rutinas/alumno/" + alumnoId)) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder peticion : escrituras(id)) {
            mvc.perform(peticion).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rutinaOAlumnoInexistentesResponden404() throws Exception {
        long inexistente = Long.MAX_VALUE;
        Long id = crear("Existente", false);
        List<MockHttpServletRequestBuilder> peticiones = List.of(
                get("/api/rutinas/{id}", inexistente),
                put("/api/rutinas/{id}", inexistente).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("No existe", false, List.of())),
                delete("/api/rutinas/{id}", inexistente),
                post("/api/rutinas/{id}/asignar/{alumno}", inexistente, alumnoId),
                post("/api/rutinas/{id}/asignar/{alumno}", id, inexistente),
                get("/api/rutinas/alumno/{id}", inexistente),
                delete("/api/rutinas/asignar/{id}", inexistente));
        for (MockHttpServletRequestBuilder peticion : peticiones) {
            mvc.perform(peticion.header("Authorization", tokenAdmin)).andExpect(status().isNotFound());
        }
    }

    @Test
    void titularNoPuedeCrearNiEditarRutinaSinNombre() throws Exception {
        Long id = crear("Valida", false);
        String invalido = cuerpo(" ", false, List.of());
        mvc.perform(post("/api/rutinas").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/rutinas/{id}", id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(invalido)).andExpect(status().isBadRequest());
        assertThat(consultar(id, tokenAdmin).get("nombre").asText()).isEqualTo("Valida");
    }

    private List<MockHttpServletRequestBuilder> escrituras(Long id) throws Exception {
        String cuerpo = cuerpo("Cambio prohibido", false, List.of());
        return List.of(post("/api/rutinas").contentType(MediaType.APPLICATION_JSON).content(cuerpo),
                put("/api/rutinas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(cuerpo),
                post("/api/rutinas/{id}/asignar/{alumno}", id, otroAlumnoId),
                delete("/api/rutinas/asignar/{id}", alumnoId), delete("/api/rutinas/{id}", id));
    }

    private void verificarSinRutina() throws Exception {
        for (String ruta : List.of("/api/rutinas/mis-rutinas", "/api/rutinas/alumno/" + alumnoId)) {
            mvc.perform(get(ruta).header("Authorization", tokenAlumno))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        mvc.perform(get("/api/rutinas/alumno/{id}", alumnoId).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    private Long crear(String nombre, boolean generica) throws Exception {
        String respuesta = mvc.perform(post("/api/rutinas").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, generica,
                        List.of(dia("Dia 1", 1, List.of(ejercicio("Sentadilla", 3, null)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();
        recargar();
        return id;
    }

    private void actualizar(Long id, String cuerpo) throws Exception {
        mvc.perform(put("/api/rutinas/{id}", id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
        recargar();
    }

    private JsonNode consultar(Long id, String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/rutinas/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void asignar(Long rutina, Long alumno) throws Exception {
        mvc.perform(post("/api/rutinas/{id}/asignar/{alumno}", rutina, alumno)
                .header("Authorization", tokenAdmin)).andExpect(status().isOk());
        recargar();
    }

    private String cuerpo(String nombre, Boolean generica, List<DiaRutinaDto> dias) throws Exception {
        return json.writeValueAsString(new RutinaRequestDto(nombre, "Plan de prueba", ObjetivoRutina.FUERZA, generica, dias));
    }

    private DiaRutinaDto dia(String nombre, int orden, List<EjercicioDto> ejercicios) {
        return new DiaRutinaDto(null, nombre, orden, ejercicios, List.of());
    }

    private EjercicioDto ejercicio(String nombre, int series, Long video) {
        return new EjercicioDto(null, nombre, series, "10", "60", "Tecnica controlada", 1,
                TipoSerieEjercicio.NORMAL, video, null);
    }

    private Usuario usuario(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(rol);
        u.setActivo(true);
        em.persist(u);
        return u;
    }

    private Alumno socio(String dni, Usuario usuario) {
        Alumno a = new Alumno();
        a.setNombre("Socio " + dni);
        a.setDni(dni);
        a.setActivo(true);
        a.setFechaAlta(LocalDate.now());
        a.setUsuario(usuario);
        usuario.setAlumno(a);
        em.persist(a);
        return a;
    }

    private String token(Usuario usuario, Long alumno) {
        return "Bearer " + jwt.generarToken(usuario.getId(), usuario.getEmail(), usuario.getRol().name(), alumno);
    }

    private void recargar() {
        em.flush();
        em.clear();
    }
}