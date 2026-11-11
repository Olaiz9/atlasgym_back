package com.atlas.backend;

import com.atlas.backend.Dto.GuardarSesionRequestDto;
import com.atlas.backend.Dto.RegistroSerieDto;
import com.atlas.backend.Dto.SesionEjercicioDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.DiaRutina;
import com.atlas.backend.Modelo.Ejercicio;
import com.atlas.backend.Modelo.ObjetivoRutina;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.DiaRutinaRepository;
import com.atlas.backend.Repositorio.EjercicioRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sesiones de entrenamiento: registro de series, pesos y repeticiones por parte del socio,
 * consulta de historial y ultima sesion por dia de rutina, y aislamiento entre alumnos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SesionesEntrenamientoTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private RutinaRepository rutinaRepository;
    @Autowired private DiaRutinaRepository diaRutinaRepository;
    @Autowired private EjercicioRepository ejercicioRepository;

    private String tokenAdmin;
    private String tokenAlumnoA;
    private String tokenAlumnoB;
    private Alumno alumnoA;
    private Alumno alumnoB;
    private Rutina rutina;
    private DiaRutina diaEmpuje;
    private DiaRutina diaTiron;
    private Ejercicio pressBanca;

    @BeforeEach
    void preparar() {
        tokenAdmin = crearTokenTitular("titular.sesiones@atlasgym.com");
        alumnoA = crearSocioConUsuario("socio.sesiones.a", "40111222", "Socio A");
        alumnoB = crearSocioConUsuario("socio.sesiones.b", "40111223", "Socio B");
        tokenAlumnoA = tokenParaSocio(alumnoA);
        tokenAlumnoB = tokenParaSocio(alumnoB);

        Rutina r = new Rutina("Fuerza 2 dias", "Rutina de prueba", ObjetivoRutina.FUERZA, true, LocalDate.now());
        rutina = rutinaRepository.saveAndFlush(r);

        diaEmpuje = diaRutinaRepository.saveAndFlush(new DiaRutina("Dia 1 - Empuje", 1, rutina));
        diaTiron = diaRutinaRepository.saveAndFlush(new DiaRutina("Dia 2 - Tiron", 2, rutina));

        Ejercicio ej = new Ejercicio();
        ej.setNombreEjercicio("Press de banca");
        ej.setSerie(3);
        ej.setRepeticiones("8-10");
        ej.setDescansoSegundos("90");
        ej.setOrden(1);
        ej.setDiaRutina(diaEmpuje);
        pressBanca = ejercicioRepository.saveAndFlush(ej);
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

    private GuardarSesionRequestDto armarDtoSesion(Long alumnoId, LocalDate fecha, double kgPrimeraSerie) {
        List<RegistroSerieDto> series = List.of(
                new RegistroSerieDto(null, 1, kgPrimeraSerie, 10, true),
                new RegistroSerieDto(null, 2, kgPrimeraSerie + 5.0, 8, true)
        );
        List<SesionEjercicioDto> ejercicios = List.of(
                new SesionEjercicioDto(null, pressBanca.getId(), null, series),
                new SesionEjercicioDto(null, null, "Fondos en paralelas", List.of(
                        new RegistroSerieDto(null, 1, 0.0, null, null)
                ))
        );
        return new GuardarSesionRequestDto(
                alumnoId,
                rutina.getId(),
                diaEmpuje.getId(),
                fecha,
                55,
                "Buena sesion de empuje",
                ejercicios
        );
    }

    @Test
    void alumnoGuardaSesionYLaVeEnSuHistorialYEnUltimaSesion() throws Exception {
        // Sin sesiones previas para ese dia de rutina devuelve 204 No Content
        mvc.perform(get("/api/sesiones/ultima")
                        .header("Authorization", tokenAlumnoA)
                        .param("alumnoId", alumnoA.getIdAlumno().toString())
                        .param("diaRutinaId", diaTiron.getId().toString()))
                .andExpect(status().isNoContent());

        GuardarSesionRequestDto sesionAnterior = armarDtoSesion(
                alumnoA.getIdAlumno(), LocalDate.now().minusDays(7), 60.0);
        mvc.perform(post("/api/sesiones")
                        .header("Authorization", tokenAlumnoA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(sesionAnterior)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alumnoId").value(alumnoA.getIdAlumno()))
                .andExpect(jsonPath("$.nombreDiaRutina").value("Dia 1 - Empuje"))
                .andExpect(jsonPath("$.ejercicios", hasSize(2)))
                .andExpect(jsonPath("$.ejercicios[0].nombreEjercicio").value("Press de banca"))
                .andExpect(jsonPath("$.ejercicios[0].series[0].kg").value(60.0));

        GuardarSesionRequestDto sesionReciente = armarDtoSesion(
                alumnoA.getIdAlumno(), LocalDate.now(), 65.0);
        mvc.perform(post("/api/sesiones")
                        .header("Authorization", tokenAlumnoA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(sesionReciente)))
                .andExpect(status().isCreated());

        // Historial del propio alumno ordenado por fecha descendente
        mvc.perform(get("/api/sesiones/historial/" + alumnoA.getIdAlumno())
                        .header("Authorization", tokenAlumnoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].ejercicios[0].series[0].kg").value(65.0))
                .andExpect(jsonPath("$[1].ejercicios[0].series[0].kg").value(60.0));

        // Ultima sesion de ese dia de rutina devuelve la mas reciente (65 kg)
        mvc.perform(get("/api/sesiones/ultima")
                        .header("Authorization", tokenAlumnoA)
                        .param("alumnoId", alumnoA.getIdAlumno().toString())
                        .param("diaRutinaId", diaEmpuje.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejercicios[0].series[0].kg").value(65.0));
    }

    @Test
    void alumnoNoPuedeGuardarNiLeerSesionesDeOtroAlumno() throws Exception {
        GuardarSesionRequestDto intentoSuplantacion = armarDtoSesion(
                alumnoB.getIdAlumno(), LocalDate.now(), 80.0);

        // 1. POST /api/sesiones a nombre de otro alumno -> 403
        mvc.perform(post("/api/sesiones")
                        .header("Authorization", tokenAlumnoA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(intentoSuplantacion)))
                .andExpect(status().isForbidden());

        // 2. GET /api/sesiones/historial/{otroId} -> 403
        mvc.perform(get("/api/sesiones/historial/" + alumnoB.getIdAlumno())
                        .header("Authorization", tokenAlumnoA))
                .andExpect(status().isForbidden());

        // 3. GET /api/sesiones/ultima con alumnoId de otro socio -> 403
        mvc.perform(get("/api/sesiones/ultima")
                        .header("Authorization", tokenAlumnoA)
                        .param("alumnoId", alumnoB.getIdAlumno().toString())
                        .param("diaRutinaId", diaEmpuje.getId().toString()))
                .andExpect(status().isForbidden());

        // Sin token -> 401
        mvc.perform(get("/api/sesiones/historial/" + alumnoA.getIdAlumno()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sesiones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(intentoSuplantacion)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void titularPuedeVerElHistorialDeCualquierAlumno() throws Exception {
        GuardarSesionRequestDto sesionB = armarDtoSesion(alumnoB.getIdAlumno(), LocalDate.now(), 72.5);
        mvc.perform(post("/api/sesiones")
                        .header("Authorization", tokenAlumnoB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(sesionB)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sesiones/historial/" + alumnoB.getIdAlumno())
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].alumnoId").value(alumnoB.getIdAlumno()))
                .andExpect(jsonPath("$[0].ejercicios[0].series[0].kg").value(72.5));

        // Alumno inexistente devuelve 404 al intentar guardar sesion como titular
        GuardarSesionRequestDto inexistente = armarDtoSesion(999999L, null, 50.0);
        mvc.perform(post("/api/sesiones")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(inexistente)))
                .andExpect(status().isNotFound());
    }
}
