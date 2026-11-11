package com.atlas.backend;

import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.atlas.backend.Servicio.PagoService;
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
import java.util.HashMap;
import java.util.Map;

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
 * Planes: definen el precio de la cuota y los dias por semana. Los editan los titulares
 * (por ejemplo en cada aumento) y de ellos dependen los cobros y el limite semanal del totem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanesTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private PagoService pagoService;
    @Autowired private EntityManager em;

    private String tokenAdmin;
    private String tokenAlumno;

    @BeforeEach
    void preparar() {
        tokenAdmin = token("titular.planes@atlasgym.com", Rol.ADMIN);
        tokenAlumno = token("socio.planes", Rol.ALUMNO);
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

    private String cuerpo(String nombre, String precio, String dias) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", nombre);
        m.put("precio", precio == null ? null : new BigDecimal(precio));
        m.put("diaPorSemana", dias);
        return json.writeValueAsString(m);
    }

    private Plan plan(String nombre, String precio) {
        return planRepository.saveAndFlush(new Plan(nombre, null, "3", new BigDecimal(precio), true));
    }

    private Alumno socio(String dni, Plan plan, boolean activo) {
        Alumno a = new Alumno();
        a.setNombre("Socio " + dni);
        a.setDni(dni);
        a.setActivo(activo);
        a.setFechaAlta(LocalDate.now().minusMonths(3));
        a.setPlanAlumno(plan);
        return alumnoRepository.saveAndFlush(a);
    }

    private PagoResponseDto cobrar(Alumno alumno, Plan plan, String monto) {
        return pagoService.registrarPago(new PagoRequestDto(
                alumno.getIdAlumno(), plan.getIdPlan(), new BigDecimal(monto), "EFECTIVO",
                LocalDate.now().minusMonths(1).toString().substring(0, 7),
                LocalDate.now().minusMonths(1), "PAGADO", 1, null, null, null));
    }

    @Test
    void titularCreaYEditaPlanesYElAlumnoLosVe() throws Exception {
        String respuesta = mvc.perform(post("/api/planes").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Musculacion 3x", "28000", "3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precio").value(28000))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(respuesta).get("id").asLong();

        mvc.perform(put("/api/planes/" + id).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Musculacion 3x", "32000", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(32000));

        mvc.perform(get("/api/planes").header("Authorization", tokenAlumno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("Musculacion 3x")));
    }

    @Test
    void alumnoNoPuedeCrearEditarNiBorrarPlanes() throws Exception {
        Plan existente = plan("Pase Libre", "40000");

        mvc.perform(post("/api/planes").header("Authorization", tokenAlumno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Plan trucho", "1", "7")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/planes/" + existente.getIdPlan()).header("Authorization", tokenAlumno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Pase Libre", "1", "7")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/planes/" + existente.getIdPlan()).header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
        // La lista de planes activos es un catalogo publico (SecurityConfig), como una lista de precios;
        // el listado completo con planes dados de baja sigue siendo solo para titulares.
        mvc.perform(get("/api/planes")).andExpect(status().isOk());
        mvc.perform(get("/api/planes/todos").header("Authorization", tokenAlumno)).andExpect(status().isForbidden());

        assertThat(planRepository.findById(existente.getIdPlan()).orElseThrow().getPrecio())
                .isEqualByComparingTo("40000");
    }

    @Test
    void rechazaPlanSinNombreSinPrecioOConPrecioNegativo() throws Exception {
        for (String invalido : new String[]{cuerpo("", "28000", "3"), cuerpo("Sin precio", null, "3"),
                cuerpo("Precio negativo", "-28000", "3")}) {
            mvc.perform(post("/api/planes").header("Authorization", tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalido))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aumentoDePrecioNoModificaLosPagosYaCobrados() throws Exception {
        Plan plan = plan("Funcional 2x", "28000");
        Alumno socio = socio("30100200", plan, true);
        PagoResponseDto pago = cobrar(socio, plan, "28000");

        mvc.perform(put("/api/planes/" + plan.getIdPlan()).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Funcional 2x", "35000", "2")))
                .andExpect(status().isOk());

        em.clear();
        PagoResponseDto despues = pagoService.buscarPorId(pago.id());
        assertThat(despues.monto()).isEqualByComparingTo("28000");
        assertThat(despues.planNombre()).isEqualTo("Funcional 2x");
    }

    @Test
    void noSeBorraUnPlanConSociosActivos() throws Exception {
        Plan plan = plan("Crossfit", "30000");
        socio("30100201", plan, true);

        mvc.perform(delete("/api/planes/" + plan.getIdPlan()).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        assertThat(planRepository.findById(plan.getIdPlan())).isPresent();
    }

    @Test
    void planConHistorialSeDesactivaEnVezDeBorrarseYLosPagosConservanSuPlan() throws Exception {
        Plan plan = plan("Plan 2025", "20000");
        Alumno exSocio = socio("30100202", plan, true);
        PagoResponseDto pagoViejo = cobrar(exSocio, plan, "20000");
        exSocio.setActivo(false); // el socio se dio de baja; el plan ya no tiene socios activos
        alumnoRepository.saveAndFlush(exSocio);

        mvc.perform(delete("/api/planes/" + plan.getIdPlan()).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();
        // El plan sigue existiendo (dado de baja) para no perder el historial de cobros
        assertThat(planRepository.findById(plan.getIdPlan())).hasValueSatisfying(p -> assertThat(p.isActivo()).isFalse());
        assertThat(pagoService.buscarPorId(pagoViejo.id()).planNombre()).isEqualTo("Plan 2025");
        assertThat(alumnoRepository.findById(exSocio.getIdAlumno()).orElseThrow().getPlanAlumno()).isNotNull();
        // ...pero ya no se ofrece a los socios
        mvc.perform(get("/api/planes").header("Authorization", tokenAlumno))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Plan 2025"))));
    }

    @Test
    void planSinUsoSeBorraDefinitivamente() throws Exception {
        Plan plan = plan("Plan cargado por error", "1");

        mvc.perform(delete("/api/planes/" + plan.getIdPlan()).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();
        assertThat(planRepository.findById(plan.getIdPlan())).isEmpty();
        mvc.perform(get("/api/planes/" + plan.getIdPlan()).header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }
}
