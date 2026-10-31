package com.atlas.backend;

import com.atlas.backend.Controlador.AsistenciaController;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.ObjetivoRutina;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.SecurityConfig;
import com.atlas.backend.Servicio.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadHardeningTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Autowired
    private RutinaRepository rutinaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private AsistenciaController asistenciaController;

    @Autowired
    private SecurityConfig securityConfig;

    @Test
    void login_bloqueaFuerzaBrutaAlSextoIntentoFallido() throws Exception {
        String identificador = "admin-bruteforce@atlasgym.com";
        authService.limpiarIntentosFallidos(identificador);

        Usuario u = new Usuario();
        u.setEmail(identificador);
        u.setPassword(passwordEncoder.encode("ClaveReal123"));
        u.setRol(Rol.ADMIN);
        u.setActivo(true);
        usuarioRepository.saveAndFlush(u);

        try {
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "email", identificador,
                                        "password", "ClaveIncorrecta" + i
                                ))))
                        .andExpect(status().isUnauthorized());
            }

            // El 6to intento queda bloqueado con HTTP 429 aunque envíe la clave correcta
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", identificador,
                                    "password", "ClaveReal123"
                            ))))
                    .andExpect(status().isTooManyRequests());

            authService.limpiarIntentosFallidos(identificador);

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", identificador,
                                    "password", "ClaveReal123"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        } finally {
            authService.limpiarIntentosFallidos(identificador);
        }
    }

    @Test
    void actualizarPerfil_exigePasswordActualValidaYBloqueaUrlInsegura() throws Exception {
        Usuario u = new Usuario();
        u.setEmail("dueno-perfil@atlasgym.com");
        u.setPassword(passwordEncoder.encode("ClaveVieja123"));
        u.setRol(Rol.ADMIN);
        u.setActivo(true);
        usuarioRepository.saveAndFlush(u);

        String token = obtenerToken("dueno-perfil@atlasgym.com", "ClaveVieja123");

        // 1. Sin passwordActual -> 400
        mvc.perform(put("/api/auth/perfil")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", "NuevaClave999"))))
                .andExpect(status().isBadRequest());

        // 2. Con passwordActual errónea -> 400
        mvc.perform(put("/api/auth/perfil")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "passwordActual", "NoEsLaVieja",
                                "password", "NuevaClave999"
                        ))))
                .andExpect(status().isBadRequest());

        // 3. Con fotoUrl maliciosa javascript: -> 400
        mvc.perform(put("/api/auth/perfil")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fotoUrl", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());

        // 4. Con passwordActual válida y fotoUrl segura -> 200
        mvc.perform(put("/api/auth/perfil")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "passwordActual", "ClaveVieja123",
                                "password", "NuevaClave999",
                                "fotoUrl", "https://ejemplo.com/avatar.png"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value("https://ejemplo.com/avatar.png"));
    }

    @Test
    void rutinas_impideIdorEntreAlumnosEnRutinasPrivadas() throws Exception {
        Rutina rutinaPrivadaA = crearRutina("Rutina Privada A", false);
        Rutina rutinaPrivadaB = crearRutina("Rutina Privada B", false);
        Rutina rutinaGenerica = crearRutina("Plantilla Genérica", true);

        crearAlumnoConUsuario("Alumno A", "45111222", "alumnoa222", rutinaPrivadaA);
        crearAlumnoConUsuario("Alumno B", "45333444", "alumnob444", rutinaPrivadaB);

        String tokenA = obtenerToken("alumnoa222", "ClaveSocio123");

        // Alumno A puede ver su propia rutina privada y plantillas genéricas
        mvc.perform(get("/api/rutinas/{id}", rutinaPrivadaA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/rutinas/{id}", rutinaGenerica.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Alumno A NO puede espiar la rutina privada de Alumno B (IDOR bloqueado -> 403)
        mvc.perform(get("/api/rutinas/{id}", rutinaPrivadaB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void videoteca_rechazaVideoSinTituloConValidacion400() throws Exception {
        Usuario admin = new Usuario();
        admin.setEmail("admin-video@atlasgym.com");
        admin.setPassword(passwordEncoder.encode("ClaveAdmin123"));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        usuarioRepository.saveAndFlush(admin);

        String tokenAdmin = obtenerToken("admin-video@atlasgym.com", "ClaveAdmin123");

        mvc.perform(post("/api/videoteca")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "titulo", "   ",
                                "grupoMuscularVideo", "PECHO"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.titulo").exists());
    }

    @Test
    void totem_validaHeaderSecretoCuandoEstaConfigurado() throws Exception {
        Alumno socio = new Alumno();
        socio.setNombre("Socio Kiosco");
        socio.setDni("46778899");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.now());
        alumnoRepository.saveAndFlush(socio);

        Object valorOriginal = ReflectionTestUtils.getField(asistenciaController, "totemSecretConfigurado");
        ReflectionTestUtils.setField(asistenciaController, "totemSecretConfigurado", "secreto-kiosco-123");
        try {
            // Sin header X-Totem-Secret -> 403
            mvc.perform(post("/api/asistencias/totem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("dni", "46778899"))))
                    .andExpect(status().isForbidden());

            // Con header incorrecto -> 403
            mvc.perform(post("/api/asistencias/totem")
                            .header("X-Totem-Secret", "secreto-falso")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("dni", "46778899"))))
                    .andExpect(status().isForbidden());

            // Con header válido -> 200
            mvc.perform(post("/api/asistencias/totem")
                            .header("X-Totem-Secret", "secreto-kiosco-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("dni", "46778899"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        } finally {
            ReflectionTestUtils.setField(
                    asistenciaController,
                    "totemSecretConfigurado",
                    valorOriginal != null ? valorOriginal : ""
            );
        }
    }

    @Test
    void cors_soportaListaSeparadaPorComasPatronesLanYRechazaOrigenesNoAutorizados() throws Exception {
        List<String> parseados = SecurityConfig.parsearOrigenesPermitidos(
                "https://app.atlasgym.com.ar/, http://192.168.*.*:3000 , *,   "
        );
        assertTrue(parseados.contains("https://app.atlasgym.com.ar"));
        assertTrue(parseados.contains("http://192.168.*.*:3000"));
        assertTrue(parseados.contains("http://localhost:3000"));
        assertFalse(parseados.contains("*"));

        Object original = ReflectionTestUtils.getField(securityConfig, "allowedOrigin");
        ReflectionTestUtils.setField(
                securityConfig,
                "allowedOrigin",
                "https://app.atlasgym.com.ar/, http://192.168.*.*:3000"
        );
        try {
            // 1. Dominio productivo con barra final en config -> normalizado y aceptado en preflight
            mvc.perform(options("/api/auth/login")
                            .header("Origin", "https://app.atlasgym.com.ar")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.atlasgym.com.ar"));

            // 2. Celular de los dueños en red Wi-Fi LAN del gimnasio -> aceptado por patrón
            mvc.perform(options("/api/auth/login")
                            .header("Origin", "http://192.168.1.45:3000")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.45:3000"));

            // 3. Origen externo malicioso -> rechazado con 403 Forbidden
            mvc.perform(options("/api/auth/login")
                            .header("Origin", "https://sitio-malicioso.evil.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        } finally {
            ReflectionTestUtils.setField(
                    securityConfig,
                    "allowedOrigin",
                    original != null ? original : "http://localhost:3000"
            );
        }
    }

    @Test
    void login_aislaFuerzaBrutaPorIpImpidiendoDosContraLosDuenos() throws Exception {
        String emailDueno = "bruno.dueno@atlasgym.com";
        authService.limpiarIntentosFallidos("*");

        Usuario dueno = new Usuario();
        dueno.setEmail(emailDueno);
        dueno.setNombreMostrar("Bruno (Dueño)");
        dueno.setPassword(passwordEncoder.encode("ClaveDuenoReal2026"));
        dueno.setRol(Rol.ADMIN);
        dueno.setActivo(true);
        usuarioRepository.saveAndFlush(dueno);

        try {
            // Un atacante desde IP externa 203.0.113.77 dispara 5 intentos fallidos contra el email del dueño
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/auth/login")
                                .with(req -> {
                                    req.setRemoteAddr("203.0.113.77");
                                    return req;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "email", emailDueno,
                                        "password", "IntentoMalicioso" + i
                                ))))
                        .andExpect(status().isUnauthorized());
            }

            // La IP atacante queda bloqueada con HTTP 429
            mvc.perform(post("/api/auth/login")
                            .with(req -> {
                                req.setRemoteAddr("203.0.113.77");
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", emailDueno,
                                    "password", "ClaveDuenoReal2026"
                            ))))
                    .andExpect(status().isTooManyRequests());

            // El dueño real desde su celular en el gimnasio (192.168.1.40) NO sufre bloqueo DoS e ingresa normal
            mvc.perform(post("/api/auth/login")
                            .with(req -> {
                                req.setRemoteAddr("192.168.1.40");
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", emailDueno,
                                    "password", "ClaveDuenoReal2026"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Bruno (Dueño)"))
                    .andExpect(jsonPath("$.token").isNotEmpty());
        } finally {
            authService.limpiarIntentosFallidos("*");
        }
    }

    @Test
    void socioNuevoYBlanqueoClave_exigeCambioPasswordEnPrimerLoginYLimpiaBanderaAlActualizar() throws Exception {
        Usuario admin = new Usuario();
        admin.setEmail("admin-alta@atlasgym.com");
        admin.setPassword(passwordEncoder.encode("ClaveAdmin123"));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        usuarioRepository.saveAndFlush(admin);

        String tokenAdmin = obtenerToken("admin-alta@atlasgym.com", "ClaveAdmin123");

        String creadoBody = mvc.perform(post("/api/alumnos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nombre", "Valentín Socio",
                                "dni", "48123456",
                                "activo", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long alumnoId = json.readTree(creadoBody).get("id").asLong();

        // 1. Primer login del socio con su DNI -> requiereCambioPassword = true
        String login1 = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "48123456",
                                "password", "48123456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tokenSocio = json.readTree(login1).get("token").asText();

        // 2. El socio define su clave personal -> requiereCambioPassword pasa a false
        mvc.perform(put("/api/auth/perfil")
                        .header("Authorization", "Bearer " + tokenSocio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "passwordActual", "48123456",
                                "password", "MiClavePrivada2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(false));

        // 3. El socio olvida su clave y recepción la blanquea en 1 clic a su DNI -> vuelve requiereCambioPassword = true
        mvc.perform(post("/api/alumnos/{id}/blanquear-clave", alumnoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "48123456",
                                "password", "48123456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true));
    }

    private Rutina crearRutina(String nombre, boolean esGenerica) {
        Rutina r = new Rutina();
        r.setNombre(nombre);
        r.setObjetivo(ObjetivoRutina.HIPERTROFIA);
        r.setEsGenerica(esGenerica);
        r.setFechaCreacion(LocalDate.now());
        return rutinaRepository.saveAndFlush(r);
    }

    private Alumno crearAlumnoConUsuario(String nombre, String dni, String username, Rutina rutina) {
        Usuario u = new Usuario();
        u.setEmail(username);
        u.setPassword(passwordEncoder.encode("ClaveSocio123"));
        u.setRol(Rol.ALUMNO);
        u.setActivo(true);
        u = usuarioRepository.saveAndFlush(u);

        Alumno a = new Alumno();
        a.setNombre(nombre);
        a.setDni(dni);
        a.setActivo(true);
        a.setFechaAlta(LocalDate.now());
        a.setUsuario(u);
        a.setRutinaAlumno(rutina);
        u.setAlumno(a);
        return alumnoRepository.saveAndFlush(a);
    }

    private String obtenerToken(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("token").asText();
    }
}
