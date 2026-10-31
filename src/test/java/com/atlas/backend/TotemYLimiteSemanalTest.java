package com.atlas.backend;

import com.atlas.backend.Controlador.AsistenciaController;
import com.atlas.backend.Dto.AsistenciaResponseDto;
import com.atlas.backend.Dto.TotemCheckInResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.MetodoAsistencia;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Servicio.AsistenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TotemYLimiteSemanalTest {

    @Autowired
    private AsistenciaService asistenciaService;

    @Autowired
    private AsistenciaRepository asistenciaRepository;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AsistenciaController asistenciaController;

    @Test
    void extraerLimiteDiasSemana_distinguePlanesConCupoDePlanesLibres() {
        assertThat(AsistenciaService.extraerLimiteDiasSemana("2 días por semana")).isEqualTo(2);
        assertThat(AsistenciaService.extraerLimiteDiasSemana("3 días")).isEqualTo(3);
        assertThat(AsistenciaService.extraerLimiteDiasSemana("5 días por semana")).isEqualTo(5);
        assertThat(AsistenciaService.extraerLimiteDiasSemana("Libre")).isNull();
        assertThat(AsistenciaService.extraerLimiteDiasSemana("Libre (Lunes a Sábado)")).isNull();
        assertThat(AsistenciaService.extraerLimiteDiasSemana("Pase Full Ilimitado")).isNull();
        assertThat(AsistenciaService.extraerLimiteDiasSemana(null)).isNull();
    }

    @Test
    void historialPorFecha_marcaExcedeLimiteSemanalEnCuartoDiaYNoDuplicaDobleTurnoDelMismoDia() {
        Plan plan3Dias = planRepository.saveAndFlush(new Plan(
                "Plan 3 Días",
                "Hasta 3 asistencias semanales",
                "3 días por semana",
                new BigDecimal("18000"),
                true
        ));

        Alumno socio = new Alumno();
        socio.setNombre("Camila 3 Días");
        socio.setDni("44556677");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.of(2026, 10, 1));
        socio.setPlanAlumno(plan3Dias);
        Alumno socioGuardado = alumnoRepository.saveAndFlush(socio);

        // Semana fija: Lunes 2026-10-26 al Jueves 2026-10-29
        LocalDate lunes = LocalDate.of(2026, 10, 26);
        LocalDate martes = LocalDate.of(2026, 10, 27);
        LocalDate miercoles = LocalDate.of(2026, 10, 28);
        LocalDate jueves = LocalDate.of(2026, 10, 29);

        // Doble ingreso el lunes (mañana y noche) -> cuenta como 1 solo día asistido
        asistenciaRepository.save(new Asistencia(lunes, LocalTime.of(8, 30), socioGuardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.save(new Asistencia(lunes, LocalTime.of(19, 30), socioGuardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.save(new Asistencia(martes, LocalTime.of(18, 0), socioGuardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.save(new Asistencia(miercoles, LocalTime.of(18, 0), socioGuardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.saveAndFlush(new Asistencia(jueves, LocalTime.of(18, 0), socioGuardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));

        // Al Miércoles lleva 3 de 3 días -> NO excede
        List<AsistenciaResponseDto> delMiercoles = asistenciaService.historialPorFecha(miercoles);
        AsistenciaResponseDto dtoMiercoles = delMiercoles.stream()
                .filter(a -> socioGuardado.getIdAlumno().equals(a.alumnoId()))
                .findFirst()
                .orElseThrow();
        assertThat(dtoMiercoles.asistenciasSemana()).isEqualTo(3);
        assertThat(dtoMiercoles.limiteDiasSemana()).isEqualTo(3);
        assertThat(dtoMiercoles.excedeLimiteSemanal()).isFalse();

        // Al Jueves lleva 4 de 3 días -> SÍ excede y avisa al módulo de asistencias
        List<AsistenciaResponseDto> delJueves = asistenciaService.historialPorFecha(jueves);
        AsistenciaResponseDto dtoJueves = delJueves.stream()
                .filter(a -> socioGuardado.getIdAlumno().equals(a.alumnoId()))
                .findFirst()
                .orElseThrow();
        assertThat(dtoJueves.asistenciasSemana()).isEqualTo(4);
        assertThat(dtoJueves.limiteDiasSemana()).isEqualTo(3);
        assertThat(dtoJueves.excedeLimiteSemanal()).isTrue();
    }

    @Test
    void checkInTotem_noBloqueaAlSocioPeroInformaExcesoSemanalYSoportaHoraPico() throws Exception {
        Plan plan2Dias = planRepository.saveAndFlush(new Plan(
                "Plan 2 Días",
                "2 veces por semana",
                "2 días por semana",
                new BigDecimal("15000"),
                true
        ));

        Alumno socio = new Alumno();
        socio.setNombre("Bruno Totem");
        socio.setDni("41223344");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.now());
        socio.setPlanAlumno(plan2Dias);
        Alumno socioGuardado = alumnoRepository.saveAndFlush(socio);

        // Sembramos 2 días distintos en la misma semana ISO distintos de hoy
        LocalDate hoy = LocalDate.now();
        LocalDate lunes = AsistenciaService.inicioSemanaLunes(hoy);
        int sembrados = 0;
        for (int offset = 0; offset < 7 && sembrados < 2; offset++) {
            LocalDate candidato = lunes.plusDays(offset);
            if (!candidato.equals(hoy)) {
                asistenciaRepository.save(new Asistencia(
                        candidato,
                        LocalTime.of(10, 0),
                        socioGuardado,
                        MetodoAsistencia.DNI_TOTEM,
                        EstadoCuenta.AL_DIA
                ));
                sembrados++;
            }
        }
        asistenciaRepository.flush();

        // Al fichar hoy en el Tótem (3er día de un plan de 2 días): ok=true y excedeLimiteSemanal=true
        mvc.perform(post("/api/asistencias/totem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("dni", "41.223.344"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.asistenciasSemana").value(3))
                .andExpect(jsonPath("$.limiteDiasSemana").value(2))
                .andExpect(jsonPath("$.excedeLimiteSemanal").value(true));

        // Hora pico: 30 consultas seguidas desde la misma tablet NO deben disparar HTTP 429
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/asistencias/totem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("dni", "999999" + String.format("%02d", i)))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void historialPorRango_filtraVentanaExactaYRechazaRangosInvertidos() {
        Alumno socio = new Alumno();
        socio.setNombre("Socio Rango");
        socio.setDni("47123987");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.of(2026, 9, 1));
        Alumno guardado = alumnoRepository.saveAndFlush(socio);

        asistenciaRepository.save(new Asistencia(LocalDate.of(2026, 9, 15), LocalTime.of(9, 0), guardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.save(new Asistencia(LocalDate.of(2026, 10, 10), LocalTime.of(10, 0), guardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));
        asistenciaRepository.saveAndFlush(new Asistencia(LocalDate.of(2026, 10, 25), LocalTime.of(18, 0), guardado, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA));

        List<AsistenciaResponseDto> ventanaOctubre = asistenciaService.historialPorRango(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );
        List<AsistenciaResponseDto> delSocio = ventanaOctubre.stream()
                .filter(a -> guardado.getIdAlumno().equals(a.alumnoId()))
                .toList();
        assertThat(delSocio).hasSize(2);
        assertThat(delSocio.get(0).fecha()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(delSocio.get(1).fecha()).isEqualTo(LocalDate.of(2026, 10, 10));

        assertThatThrownBy(() -> asistenciaService.historialPorRango(
                LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 10, 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desde");
    }

    @Test
    void padronCacheYSyncOffline_exigeSecretoTotemYSincronizaOutboxDeFormaIdempotente() throws Exception {
        Alumno socio = new Alumno();
        socio.setNombre("Socio Outbox");
        socio.setDni("43998877");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.of(2026, 10, 1));
        Alumno guardado = alumnoRepository.saveAndFlush(socio);

        Object originalSecret = ReflectionTestUtils.getField(asistenciaController, "totemSecretConfigurado");
        try {
            // 1. Incluso si el secreto está vacío en servidor, padron-cache NO filtra el padrón públicamente sin autenticación
            ReflectionTestUtils.setField(asistenciaController, "totemSecretConfigurado", "");
            mvc.perform(get("/api/asistencias/totem/padron-cache"))
                    .andExpect(status().isForbidden());

            // 2. Con secreto configurado, requiere coincidencia exacta en X-Totem-Secret
            ReflectionTestUtils.setField(asistenciaController, "totemSecretConfigurado", "tablet-recepcion-99");
            mvc.perform(get("/api/asistencias/totem/padron-cache")
                            .header("X-Totem-Secret", "incorrecto"))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/api/asistencias/totem/padron-cache")
                            .header("X-Totem-Secret", "tablet-recepcion-99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.dni == '43998877')].nombre").isNotEmpty());

            // 3. Sincronización Outbox offline: primera entrega inserta 1 registro; reintento idempotente con el mismo clientEventId inserta 0
            Map<String, Object> lotePayload = Map.of(
                    "eventos", List.of(
                            Map.of(
                                    "clientEventId", "evt-tablet-unique-001",
                                    "dni", "43.998.877",
                                    "fecha", "2026-10-30",
                                    "hora", "17:45:00"
                            )
                    )
            );

            mvc.perform(post("/api/asistencias/totem/sync")
                            .header("X-Totem-Secret", "tablet-recepcion-99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(lotePayload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.registradosNuevos").value(1))
                    .andExpect(jsonPath("$.yaExistentes").value(0))
                    .andExpect(jsonPath("$.clientEventIdsProcesados[0]").value("evt-tablet-unique-001"));

            // Reintento por red 4G inestable del mismo evento -> idempotencia exacta
            mvc.perform(post("/api/asistencias/totem/sync")
                            .header("X-Totem-Secret", "tablet-recepcion-99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(lotePayload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.registradosNuevos").value(0))
                    .andExpect(jsonPath("$.yaExistentes").value(1))
                    .andExpect(jsonPath("$.clientEventIdsProcesados[0]").value("evt-tablet-unique-001"));

            assertThat(asistenciaRepository.findByAlumnoIdAndFechaOrderByHoraDesc(
                    guardado.getIdAlumno(),
                    LocalDate.of(2026, 10, 30)
            )).hasSize(1);
        } finally {
            ReflectionTestUtils.setField(
                    asistenciaController,
                    "totemSecretConfigurado",
                    originalSecret != null ? originalSecret : ""
            );
        }
    }
}
