package com.atlas.backend;

import com.atlas.backend.Controlador.SaludController;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración, inyección y seguridad para el endpoint público de salud /api/salud (Issue #9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaludTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;

    private String tokenAdmin;
    private String tokenAlumno;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withUserConfiguration(SaludController.class);

    @BeforeEach
    void preparar() {
        tokenAdmin = token("titular.salud@atlasgym.com", Rol.ADMIN);
        tokenAlumno = token("alumno.salud@atlasgym.com", Rol.ALUMNO);
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

    @Test
    @DisplayName("1. GET /api/salud sin token responde 200 con estado y base en ok")
    void getSaludSinTokenResponde200YOk() throws Exception {
        mvc.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"))
                .andExpect(jsonPath("$.base").value("ok"));
    }

    @Test
    @DisplayName("2. El JSON de /api/salud contiene exactamente los cuatro campos acordados")
    void jsonContieneExactamenteCuatroCampos() throws Exception {
        String contenido = mvc.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = json.readTree(contenido);
        assertThat(root.isObject()).isTrue();
        assertThat(root.size()).isEqualTo(4);
        assertThat(root.has("estado")).isTrue();
        assertThat(root.has("base")).isTrue();
        assertThat(root.has("version")).isTrue();
        assertThat(root.has("commit")).isTrue();
        assertThat(root.get("estado").asText()).isEqualTo("ok");
        assertThat(root.get("base").asText()).isEqualTo("ok");
    }

    @Test
    @DisplayName("3. Fallo simulado de DataAccessException responde 503 sin exponer detalles internos")
    void falloSimuladoBaseResponde503SinExponerDetalles() throws Exception {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("Conexion rechazada a la base"));

        Properties props = new Properties();
        props.setProperty("version", "0.0.1-SNAPSHOT");
        BuildProperties bp = new BuildProperties(props);

        SaludController controller = new SaludController(mockJdbc, Optional.of(bp), "a1b2c3d");
        MockMvc standaloneMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String respuesta = standaloneMvc.perform(get("/api/salud"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value("error"))
                .andExpect(jsonPath("$.base").value("error"))
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.commit").value("a1b2c3d"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = json.readTree(respuesta);
        assertThat(root.size()).isEqualTo(4);
        assertThat(respuesta).doesNotContain("CannotGetJdbcConnectionException");
        assertThat(respuesta).doesNotContain("Conexion rechazada");

        // También probamos otra DataAccessException (ej. timeout de lock) usando doThrow
        doThrow(new CannotAcquireLockException("Lock wait timeout"))
                .when(mockJdbc).queryForObject("SELECT 1", Integer.class);
        standaloneMvc.perform(get("/api/salud"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value("error"))
                .andExpect(jsonPath("$.base").value("error"));
    }

    @Test
    @DisplayName("4. Inyección opcional de BuildProperties mediante contexto Spring (con y sin bean)")
    void inyeccionOpcionalBuildPropertiesContextoSpring() {
        // Caso A: Contexto Spring SIN bean BuildProperties
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SaludController.class);

            SaludController controller = context.getBean(SaludController.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

            ResponseEntity<SaludController.SaludResponse> respuesta = controller.comprobarSalud();
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(respuesta.getBody()).isNotNull();
            assertThat(respuesta.getBody().estado()).isEqualTo("ok");
            assertThat(respuesta.getBody().base()).isEqualTo("ok");
            assertThat(respuesta.getBody().version()).isNull();
        });

        // Caso B: Contexto Spring CON bean BuildProperties y commit en properties
        contextRunner
                .withPropertyValues("atlas.version.commit=7f842d7")
                .withBean(BuildProperties.class, () -> {
                    Properties p = new Properties();
                    p.setProperty("version", "1.2.3");
                    return new BuildProperties(p);
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SaludController.class);

                    SaludController controller = context.getBean(SaludController.class);
                    JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

                    ResponseEntity<SaludController.SaludResponse> respuesta = controller.comprobarSalud();
                    assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(respuesta.getBody()).isNotNull();
                    assertThat(respuesta.getBody().estado()).isEqualTo("ok");
                    assertThat(respuesta.getBody().base()).isEqualTo("ok");
                    assertThat(respuesta.getBody().version()).isEqualTo("1.2.3");
                    assertThat(respuesta.getBody().commit()).isEqualTo("7f842d7");
                });
    }

    @Test
    @DisplayName("4b. Metadatos ausentes (null o blanco) devuelven null en JSON")
    void metadatosAusentesDevuelvenNullEnJson() throws Exception {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        SaludController sinMetadatos = new SaludController(mockJdbc, Optional.empty(), "   ");
        MockMvc mvcSinMeta = MockMvcBuilders.standaloneSetup(sinMetadatos).build();

        String jsonSinMeta = mvcSinMeta.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"))
                .andExpect(jsonPath("$.base").value("ok"))
                .andExpect(jsonPath("$.version").value((Object) null))
                .andExpect(jsonPath("$.commit").value((Object) null))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode rootSinMeta = json.readTree(jsonSinMeta);
        assertThat(rootSinMeta.size()).isEqualTo(4);
        assertThat(rootSinMeta.get("version").isNull()).isTrue();
        assertThat(rootSinMeta.get("commit").isNull()).isTrue();
    }

    @Test
    @DisplayName("5. POST, PUT, PATCH y DELETE no permitidos (401 sin token, 403 con token ADMIN y ALUMNO)")
    void metodosNoPermitidosRechazados() throws Exception {
        // Sin token: la cadena de seguridad rechaza con 401 (entry point HttpStatusEntryPoint)
        mvc.perform(post("/api/salud")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/salud")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/salud")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/salud")).andExpect(status().isUnauthorized());

        // Con token ADMIN: rechaza con 403 Forbidden por la regla denyAll() en SecurityConfig
        mvc.perform(post("/api/salud").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/salud").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/salud").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/salud").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());

        // Con token ALUMNO: rechaza con 403 Forbidden por la regla denyAll() en SecurityConfig
        mvc.perform(post("/api/salud").header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/salud").header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/salud").header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/salud").header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Los demás permisos de seguridad siguen funcionando correctamente")
    void demasPermisosSiguenFuncionando() throws Exception {
        // Rutas protegidas siguen rechazando sin token
        mvc.perform(get("/api/alumnos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pagos/arqueo")).andExpect(status().isUnauthorized());

        // Rutas públicas siguen funcionando sin token
        mvc.perform(get("/api/planes")).andExpect(status().isOk());
    }
}
