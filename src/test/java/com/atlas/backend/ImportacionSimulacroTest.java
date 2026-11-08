package com.atlas.backend;

import com.atlas.backend.Dto.AlumnoImportResponseDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simulacro de la importación del Día Cero ({@code POST /api/alumnos/importar?simular=true}).
 * Sin @Transactional a propósito: el simulacro abre y deshace su propia transacción, y hay que
 * comprobar que de verdad no quedó nada guardado. Lo que crea la importación real se borra al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImportacionSimulacroTest {

    private static final List<String> DNIS = List.of("45100100", "45100200", "45100300");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<Usuario> usuariosCreados = new ArrayList<>();
    private Plan plan;

    @AfterEach
    void limpiar() {
        String dnis = "'" + String.join("','", DNIS) + "'";
        List<Long> usuarios = jdbc.queryForList(
                "SELECT usuario_id FROM alumnos WHERE dni IN (" + dnis + ") AND usuario_id IS NOT NULL", Long.class);
        jdbc.update("DELETE FROM pagos WHERE alumno_id IN (SELECT id_alumno FROM alumnos WHERE dni IN (" + dnis + "))");
        jdbc.update("DELETE FROM alumnos WHERE dni IN (" + dnis + ")");
        usuarios.forEach(id -> jdbc.update("DELETE FROM usuarios WHERE id = ?", id));
        usuariosCreados.forEach(u -> jdbc.update("DELETE FROM usuarios WHERE id = ?", u.getId()));
        usuariosCreados.clear();
        if (plan != null) {
            jdbc.update("DELETE FROM planes WHERE id_plan = ?", plan.getIdPlan());
            plan = null;
        }
    }

    @Test
    @DisplayName("El simulacro no guarda nada y da exactamente lo mismo que la importación real")
    void simulacro_noGuardaNada_yCoincideConLaImportacionReal() throws Exception {
        plan = new Plan();
        plan.setNombre("Pase Libre Simulacro");
        plan.setPrecio(new BigDecimal("30000"));
        plan.setDiaPorSemana("Libre");
        plan.setActivo(true);
        plan = planRepository.saveAndFlush(plan);

        String token = tokenDe(Rol.ADMIN, "titular.simulacro@atlasgym.com");
        long usuariosAntes = usuarioRepository.count();
        long pagosAntes = pagoRepository.count();

        String mesActual = YearMonth.now().toString();
        String haceDosMeses = YearMonth.now().minusMonths(2).toString();
        Map<String, Object> payload = Map.of("filas", List.of(
                fila("Sofía Simulacro", "45.100.100", mesActual),
                fila("Marcos Simulacro", "45100200", haceDosMeses),
                fila("Lía Simulacro", "45100300", null),
                // Repetido: cuenta una vez en el resumen, con los datos de la última fila
                fila("Sofía Simulacro Bis", "45100100", mesActual),
                // Inválida: se omite y se avisa
                fila("X", "12", null)
        ));

        AlumnoImportResponseDto simulado = importar(token, payload, true);

        assertThat(simulado.simulacion()).isTrue();
        assertThat(simulado.procesados()).isEqualTo(4);
        assertThat(simulado.creados()).isEqualTo(3);
        assertThat(simulado.actualizados()).isEqualTo(1);
        assertThat(simulado.omitidos()).isEqualTo(1);
        assertThat(simulado.pagosInicialesRegistrados()).isEqualTo(2);
        assertThat(simulado.estados()).containsKeys(EstadoCuenta.values());
        assertThat(simulado.estados().get(EstadoCuenta.AL_DIA)).isEqualTo(1);
        assertThat(simulado.estados().get(EstadoCuenta.MOROSO)).isEqualTo(1);
        assertThat(simulado.estados().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(simulado.socios()).extracting(AlumnoImportResponseDto.SocioImportado::dni)
                .containsExactly("45100100", "45100200", "45100300");
        AlumnoImportResponseDto.SocioImportado sofia = simulado.socios().get(0);
        assertThat(sofia.nombre()).isEqualTo("Sofía Simulacro Bis");
        assertThat(sofia.fila()).isEqualTo(4);
        assertThat(sofia.nuevo()).isTrue();
        assertThat(sofia.plan()).isEqualTo("Pase Libre Simulacro");
        assertThat(simulado.socios().get(1).estado()).isEqualTo(EstadoCuenta.MOROSO);

        // No quedó nada guardado
        for (String dni : DNIS) {
            assertThat(alumnoRepository.findByDni(dni)).as("socio %s", dni).isEmpty();
        }
        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(pagoRepository.count()).isEqualTo(pagosAntes);

        // La importación real da el mismo resultado (salvo la marca de simulacro) y sí guarda
        AlumnoImportResponseDto real = importar(token, payload, false);

        assertThat(real.simulacion()).isFalse();
        assertThat(real.procesados()).isEqualTo(simulado.procesados());
        assertThat(real.creados()).isEqualTo(simulado.creados());
        assertThat(real.actualizados()).isEqualTo(simulado.actualizados());
        assertThat(real.omitidos()).isEqualTo(simulado.omitidos());
        assertThat(real.pagosInicialesRegistrados()).isEqualTo(simulado.pagosInicialesRegistrados());
        assertThat(real.advertencias()).isEqualTo(simulado.advertencias());
        assertThat(real.estados()).isEqualTo(simulado.estados());
        assertThat(real.socios()).isEqualTo(simulado.socios());

        for (String dni : DNIS) {
            assertThat(alumnoRepository.findByDni(dni)).as("socio %s", dni).isPresent();
        }
        Alumno guardada = alumnoRepository.findByDni("45100100").orElseThrow();
        assertThat(guardada.getUsuario().getPassword()).isNotEqualTo("{simulacro}");
    }

    @Test
    @DisplayName("Un alumno no puede simular la importación")
    void simulacro_soloParaTitulares() throws Exception {
        String token = tokenDe(Rol.ALUMNO, "alumno.simulacro@atlasgym.com");
        mvc.perform(post("/api/alumnos/importar")
                        .param("simular", "true")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("filas", List.of(fila("Lía Simulacro", "45100300", null))))))
                .andExpect(status().isForbidden());
        assertThat(alumnoRepository.findByDni("45100300")).isEmpty();
    }

    private AlumnoImportResponseDto importar(String token, Map<String, Object> payload, boolean simular) throws Exception {
        String cuerpo = mvc.perform(post("/api/alumnos/importar")
                        .param("simular", String.valueOf(simular))
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(cuerpo, AlumnoImportResponseDto.class);
    }

    private String tokenDe(Rol rol, String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(rol);
        u.setActivo(true);
        u = usuarioRepository.saveAndFlush(u);
        usuariosCreados.add(u);
        return "Bearer " + jwt.generarToken(u.getId(), u.getEmail(), rol.name(), null);
    }

    private static Map<String, Object> fila(String nombre, String dni, String ultimoPeriodo) {
        Map<String, Object> f = new HashMap<>();
        f.put("nombre", nombre);
        f.put("dni", dni);
        f.put("planNombre", "Pase Libre Simulacro");
        if (ultimoPeriodo != null) {
            f.put("ultimoPeriodoPago", ultimoPeriodo);
            f.put("metodoPago", "Efectivo");
        }
        return f;
    }
}
