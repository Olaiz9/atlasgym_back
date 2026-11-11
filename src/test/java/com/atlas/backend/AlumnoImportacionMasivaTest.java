package com.atlas.backend;

import com.atlas.backend.Dto.AlumnoImportItemDto;
import com.atlas.backend.Dto.AlumnoImportResponseDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.atlas.backend.Servicio.AlumnoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlumnoImportacionMasivaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtService jwt;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AlumnoService alumnoService;

    @Autowired
    private PagoRepository pagoRepository;

    @Test
    void importarPadronMasivo_creaSociosAsignaPlanesSiembraPagosInicialesYExigeCambioDeClave() throws Exception {
        Usuario admin = new Usuario();
        admin.setEmail("bruno.import@atlasgym.com");
        admin.setNombreMostrar("Bruno (Dueño)");
        admin.setPassword("hash-test");
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin = usuarioRepository.saveAndFlush(admin);
        String tokenAdmin = "Bearer " + jwt.generarToken(admin.getId(), admin.getEmail(), "ADMIN", null);

        Plan planLibre = new Plan();
        planLibre.setNombre("Pase Libre");
        planLibre.setPrecio(new BigDecimal("30000"));
        planLibre.setDiaPorSemana("Libre");
        planLibre.setActivo(true);
        planRepository.saveAndFlush(planLibre);

        String mesActual = YearMonth.now().toString();
        String haceDosMeses = YearMonth.now().minusMonths(2).toString();

        Map<String, Object> payload = Map.of(
                "filas", List.of(
                        Map.of(
                                "nombre", "Camila Sosa",
                                "dni", "42.111.222",
                                "celular", "011 15-4455-6677",
                                "planNombre", "Pase Libre",
                                "ultimoPeriodoPago", mesActual,
                                "metodoPago", "Transferencia"
                        ),
                        Map.of(
                                "nombre", "Nicolás Gómez",
                                "dni", "39888777",
                                "celular", "2614556677",
                                "planNombre", "Pase Libre",
                                "ultimoPeriodoPago", haceDosMeses,
                                "metodoPago", "Efectivo"
                        ),
                        // Duplicado en el mismo CSV -> actualiza sin romper la transacción
                        Map.of(
                                "nombre", "Camila Sosa Actualizada",
                                "dni", "42111222",
                                "celular", "1144556677",
                                "planNombre", "Pase Libre",
                                "ultimoPeriodoPago", mesActual
                        ),
                        // Fila inválida -> se omite y reporta en advertencias
                        Map.of(
                                "nombre", "X",
                                "dni", "12"
                        )
                )
        );

        mvc.perform(post("/api/alumnos/importar")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesados").value(3))
                .andExpect(jsonPath("$.creados").value(2))
                .andExpect(jsonPath("$.actualizados").value(1))
                .andExpect(jsonPath("$.pagosInicialesRegistrados").value(2))
                .andExpect(jsonPath("$.omitidos").value(1))
                .andExpect(jsonPath("$.advertencias.length()").value(2))
                .andExpect(jsonPath("$.advertencias[0]").value(org.hamcrest.Matchers.containsString("repetido")));

        AlumnoResponseDto camila = alumnoService.buscarPorDni("42111222");
        assertThat(camila.nombre()).isEqualTo("Camila Sosa Actualizada");
        assertThat(camila.estadoCuenta()).isEqualTo(EstadoCuenta.AL_DIA);

        AlumnoResponseDto nicolas = alumnoService.buscarPorDni("39888777");
        assertThat(nicolas.estadoCuenta()).isEqualTo(EstadoCuenta.MOROSO);

        // El socio importado puede iniciar sesión con su DNI y recibe requiereCambioPassword = true
        mvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "198.51.100.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "42111222",
                                "password", "42111222"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true));
    }

    // ---- Planilla "sucia" del Día Cero: cada caso es un error real que se puede colar del Excel ----

    private Plan crearPlan(String nombre, String precio) {
        Plan plan = new Plan();
        plan.setNombre(nombre);
        plan.setPrecio(new BigDecimal(precio));
        plan.setDiaPorSemana("3");
        plan.setActivo(true);
        return planRepository.saveAndFlush(plan);
    }

    private AlumnoImportItemDto fila(String nombre, String dni, String email, String plan, String periodo, BigDecimal monto) {
        return new AlumnoImportItemDto(nombre, dni, null, email, plan, periodo, monto, null);
    }

    @Test
    void reimportarSinPlanOConPlanDesconocido_noLePisaElPlanAlSocioYAvisa() {
        crearPlan("Plan Por Defecto Import", "15000"); // se crea primero: seria el plan "por defecto"
        Plan tresDias = crearPlan("Tres Dias Import", "20000");

        alumnoService.importarPadronMasivo(List.of(fila("Ana Paz", "40111222", null, "Tres Dias Import", null, null)), "test");
        assertThat(alumnoService.buscarPorDni("40111222").planId()).isEqualTo(tresDias.getIdPlan());

        // Segunda carga de la misma planilla, sin la columna Plan: el socio conserva su plan
        alumnoService.importarPadronMasivo(List.of(fila("Ana Paz", "40111222", null, null, null, null)), "test");
        assertThat(alumnoService.buscarPorDni("40111222").planId()).isEqualTo(tresDias.getIdPlan());

        // Plan con un nombre que no existe: se conserva el plan y se avisa cuál no se encontró
        AlumnoImportResponseDto res = alumnoService.importarPadronMasivo(
                List.of(fila("Ana Paz", "40111222", null, "Zumba Kids", null, null)), "test");
        assertThat(alumnoService.buscarPorDni("40111222").planId()).isEqualTo(tresDias.getIdPlan());
        assertThat(res.advertencias()).anyMatch(a -> a.contains("Zumba Kids"));
    }

    @Test
    void socioNuevoConPlanDesconocido_recibeElPlanPorDefectoPeroQuedaAvisado() {
        crearPlan("Pase Libre Import", "30000");
        AlumnoImportResponseDto res = alumnoService.importarPadronMasivo(
                List.of(fila("Beto Luna", "40222333", null, "Plan Inventado", null, null)), "test");
        assertThat(res.creados()).isEqualTo(1);
        assertThat(res.advertencias()).anyMatch(a -> a.contains("Plan Inventado"));
    }

    @Test
    void periodoMuyLejanoEnElFuturo_noSiembraPagoYAvisa() {
        crearPlan("Pase Libre Futuro", "30000");
        String lejano = YearMonth.now().plusMonths(13).toString();
        AlumnoImportResponseDto res = alumnoService.importarPadronMasivo(
                List.of(fila("Caro Rios", "40333444", null, "Pase Libre Futuro", lejano, null)), "test");
        assertThat(res.pagosInicialesRegistrados()).isZero();
        assertThat(res.advertencias()).anyMatch(a -> a.contains(lejano));
        Long id = alumnoService.buscarPorDni("40333444").id();
        assertThat(pagoRepository.findByAlumnoIdOrderByFechaDesc(id)).isEmpty();
    }

    @Test
    void sinMontoYConPlanSinPrecio_noInventaUnMontoEnLaCaja() {
        crearPlan("Plan Becado Import", "0");
        AlumnoImportResponseDto res = alumnoService.importarPadronMasivo(
                List.of(fila("Dani Sol", "40444555", null, "Plan Becado Import", YearMonth.now().toString(), null)), "test");
        Long id = alumnoService.buscarPorDni("40444555").id();
        assertThat(pagoRepository.findByAlumnoIdOrderByFechaDesc(id))
                .noneMatch(p -> p.getMonto().compareTo(new BigDecimal("25000")) == 0);
        assertThat(res.pagosInicialesRegistrados()).isZero();
        assertThat(res.advertencias()).anyMatch(a -> a.contains("monto"));
    }

    @Test
    void emailInvalidoDeLaPlanilla_noSeGuardaYSeAvisa() {
        crearPlan("Pase Libre Mail", "30000");
        AlumnoImportResponseDto res = alumnoService.importarPadronMasivo(
                List.of(fila("Eva Mar", "40555666", "no tiene", "Pase Libre Mail", null, null)), "test");
        assertThat(alumnoService.buscarPorDni("40555666").email()).isNull();
        assertThat(res.advertencias()).anyMatch(a -> a.contains("no tiene"));
    }
}
