package com.atlas.backend;

import com.atlas.backend.Controlador.AsistenciaController;
import com.atlas.backend.Dto.TotemEstadoDto;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.TotemLatidoRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.atlas.backend.Servicio.TotemMonitorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Si la tablet del Tótem se apaga o pierde el WiFi en horario de apertura, los titulares se enteran. */
@SpringBootTest(properties = {"atlas.totem.horario=08:00-22:00", "atlas.totem.alerta-minutos=5"})
@AutoConfigureMockMvc
@Transactional
class TotemLatidoTest {

    private static final String SECRETO = "tablet-mostrador-latido";

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TotemLatidoRepository latidoRepository;
    @Autowired private TotemMonitorService monitor;
    @Autowired private AsistenciaController asistenciaController;

    private Object secretoOriginal;
    private String tokenAdmin;
    private String tokenAlumno;

    @BeforeEach
    void preparar() {
        secretoOriginal = ReflectionTestUtils.getField(asistenciaController, "totemSecretConfigurado");
        ReflectionTestUtils.setField(asistenciaController, "totemSecretConfigurado", SECRETO);
        latidoRepository.deleteAll();

        tokenAdmin = "Bearer " + jwt.generarToken(crearUsuario("titular.latido@atlasgym.com", Rol.ADMIN), "titular.latido@atlasgym.com", "ADMIN", null);
        tokenAlumno = "Bearer " + jwt.generarToken(crearUsuario("socio.latido", Rol.ALUMNO), "socio.latido", "ALUMNO", null);
    }

    @AfterEach
    void restaurar() {
        ReflectionTestUtils.setField(asistenciaController, "totemSecretConfigurado", secretoOriginal);
    }

    private Long crearUsuario(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-test");
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.saveAndFlush(u).getId();
    }

    @Test
    void sinSenalNuncaHuboTablet_yConSenalQuedaEnLinea() throws Exception {
        mvc.perform(get("/api/asistencias/totem/estado").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NUNCA_CONECTADO"));

        mvc.perform(post("/api/asistencias/totem/latido")
                        .header("X-Totem-Secret", SECRETO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pendientesOffline\":3}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/asistencias/totem/estado").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_LINEA"))
                .andExpect(jsonPath("$.pendientesOffline").value(3))
                .andExpect(jsonPath("$.minutosAlerta").value(5));
    }

    @Test
    void laSenalExigeLaClaveDeLaTablet_yElEstadoEsSoloParaTitulares() throws Exception {
        mvc.perform(post("/api/asistencias/totem/latido").header("X-Totem-Secret", "otra-clave"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/asistencias/totem/latido"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/asistencias/totem/estado").header("Authorization", tokenAlumno))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/asistencias/totem/estado"))
                .andExpect(status().isUnauthorized());

        // Sin cuerpo también vale (cuenta como 0 pendientes)
        mvc.perform(post("/api/asistencias/totem/latido").header("X-Totem-Secret", SECRETO))
                .andExpect(status().isNoContent());
    }

    @Test
    void sinSenalEnHorarioAlarma_peroDeNocheNo() {
        LocalDateTime ultima = LocalDateTime.of(2026, 11, 9, 18, 0);
        monitor.registrarLatido(0, ultima);

        assertThat(monitor.obtenerEstado(ultima.plusMinutes(4)).estado()).isEqualTo(TotemMonitorService.EN_LINEA);

        TotemEstadoDto caido = monitor.obtenerEstado(ultima.plusMinutes(5));
        assertThat(caido.estado()).isEqualTo(TotemMonitorService.SIN_SENAL);
        assertThat(caido.minutosSinSenal()).isEqualTo(5L);
        assertThat(caido.ultimoLatido()).isEqualTo(ultima);

        // A las 23:30 el gimnasio cerró: la tablet apagada no es una alarma
        assertThat(monitor.obtenerEstado(ultima.withHour(23).withMinute(30)).estado())
                .isEqualTo(TotemMonitorService.FUERA_DE_HORARIO);
        // A la mañana siguiente, ya abierto, sí vuelve a alarmar
        assertThat(monitor.obtenerEstado(ultima.plusDays(1).withHour(8).withMinute(15)).estado())
                .isEqualTo(TotemMonitorService.SIN_SENAL);
    }

    @Test
    void horarioQueCruzaLaMedianoche_yHorarioMalEscrito() {
        TotemMonitorService nocturno = new TotemMonitorService(latidoRepository, 5, "18:00-02:00");
        assertThat(nocturno.dentroDeHorario(LocalTime.of(23, 0))).isTrue();
        assertThat(nocturno.dentroDeHorario(LocalTime.of(1, 59))).isTrue();
        assertThat(nocturno.dentroDeHorario(LocalTime.of(2, 0))).isFalse();
        assertThat(nocturno.dentroDeHorario(LocalTime.of(12, 0))).isFalse();

        TotemMonitorService malEscrito = new TotemMonitorService(latidoRepository, 5, "de 8 a 22");
        assertThat(malEscrito.dentroDeHorario(LocalTime.of(3, 0))).isTrue();
    }
}
