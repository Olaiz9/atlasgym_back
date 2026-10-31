package com.atlas.backend;

import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.atlas.backend.Servicio.AlumnoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CajaYContabilidadRealTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtService jwt;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private AlumnoService alumnoService;

    private String tokenBruno;
    private String tokenHermano2;
    private Alumno socioA;
    private Alumno socioB;

    @BeforeEach
    void preparar() {
        Usuario bruno = new Usuario();
        bruno.setEmail("bruno.caja@atlasgym.com");
        bruno.setNombreMostrar("Bruno (Dueño)");
        bruno.setPassword("hash-test");
        bruno.setRol(Rol.ADMIN);
        bruno.setActivo(true);
        bruno = usuarioRepository.saveAndFlush(bruno);
        tokenBruno = "Bearer " + jwt.generarToken(bruno.getId(), bruno.getEmail(), "ADMIN", null);

        Usuario hermano2 = new Usuario();
        hermano2.setEmail("matias.caja@atlasgym.com");
        hermano2.setNombreMostrar("Matías (Dueño)");
        hermano2.setPassword("hash-test");
        hermano2.setRol(Rol.ADMIN);
        hermano2.setActivo(true);
        hermano2 = usuarioRepository.saveAndFlush(hermano2);
        tokenHermano2 = "Bearer " + jwt.generarToken(hermano2.getId(), hermano2.getEmail(), "ADMIN", null);

        socioA = new Alumno();
        socioA.setNombre("Socio Caja A");
        socioA.setDni("41222333");
        socioA.setActivo(true);
        socioA.setFechaAlta(LocalDate.now());
        socioA = alumnoRepository.saveAndFlush(socioA);

        socioB = new Alumno();
        socioB.setNombre("Socio Caja B");
        socioB.setDni("41444555");
        socioB.setActivo(true);
        socioB.setFechaAlta(LocalDate.now());
        socioB = alumnoRepository.saveAndFlush(socioB);
    }

    @Test
    void guardiaAntiDuplicado_rechazaDobleCobroMismoPeriodoYSolapamientoMultiMesCon409() throws Exception {
        String mesActual = YearMonth.now().toString();
        String mesSiguiente = YearMonth.now().plusMonths(1).toString();

        // Cobro trimestral desde mesActual (cubre mesActual, +1, +2)
        mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 75000,
                                "metodo", "EFECTIVO",
                                "periodoMes", mesActual,
                                "cantidadMeses", 3
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoPor").value("Bruno (Dueño)"));

        // Doble clic en el mismo mesActual -> 409 Conflict
        mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 28000,
                                "metodo", "TRANSFERENCIA",
                                "periodoMes", mesActual
                        ))))
                .andExpect(status().isConflict());

        // Intento de cobrar mesSiguiente (ya cubierto dentro del trimestre) -> 409 Conflict
        mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 28000,
                                "metodo", "TRANSFERENCIA",
                                "periodoMes", mesSiguiente
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void seniaParcial_mantieneSocioPendienteHastaCompletarSaldoYLuegoPasaAlDia() throws Exception {
        String mesActual = YearMonth.now().toString();

        String respSenia = mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 28000,
                                "montoAbonado", 10000,
                                "metodo", "EFECTIVO",
                                "periodoMes", mesActual
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PARCIAL"))
                .andExpect(jsonPath("$.montoAbonado").value(10000))
                .andExpect(jsonPath("$.saldoPendiente").value(18000))
                .andReturn().getResponse().getContentAsString();

        long pagoId = json.readTree(respSenia).get("id").asLong();

        // Mientras debe $18.000 de saldo, su estado de cuenta es PENDIENTE (no AL_DIA ni INACTIVO)
        assertThat(alumnoService.calcularEstadoCuenta(socioA)).isEqualTo(EstadoCuenta.PENDIENTE);

        // Completa los $18.000 restantes por Transferencia
        mvc.perform(post("/api/pagos/{id}/completar", pagoId)
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("metodo", "TRANSFERENCIA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.montoAbonado").value(28000))
                .andExpect(jsonPath("$.saldoPendiente").value(0))
                .andExpect(jsonPath("$.comprobante").value("Seña $10000 (EFECTIVO) + Saldo $18000 (TRANSFERENCIA)"));

        assertThat(alumnoService.calcularEstadoCuenta(socioA)).isEqualTo(EstadoCuenta.AL_DIA);
    }

    @Test
    void anulacionAuditada_exigeMotivoPreservaFilaEnBaseYPermiteRecobrarPeriodo() throws Exception {
        String mesActual = YearMonth.now().toString();

        String resp = mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 50000,
                                "metodo", "EFECTIVO",
                                "periodoMes", mesActual
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long pagoErroneoId = json.readTree(resp).get("id").asLong();
        assertThat(alumnoService.calcularEstadoCuenta(socioA)).isEqualTo(EstadoCuenta.AL_DIA);

        // Motivo vacío -> 400 Bad Request
        mvc.perform(post("/api/pagos/{id}/anular", pagoErroneoId)
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Anulación con motivo válido -> preserva fila en MySQL con autor y timestamp
        mvc.perform(post("/api/pagos/{id}/anular", pagoErroneoId)
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "Importe mal tipeado ($50.000 en lugar de $28.000)"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.anuladoPor").value("Matías (Dueño)"))
                .andExpect(jsonPath("$.fechaAnulacion").isNotEmpty())
                .andExpect(jsonPath("$.motivoAnulacion").value("Importe mal tipeado ($50.000 en lugar de $28.000)"));

        Pago filaEnDb = pagoRepository.findById(pagoErroneoId).orElseThrow();
        assertThat(filaEnDb.getEstadoPago()).isEqualTo(EstadoPago.ANULADO);
        assertThat(alumnoService.calcularEstadoCuenta(socioA)).isEqualTo(EstadoCuenta.PENDIENTE);

        // Ahora sí permite recobrar el mismo período con el monto real
        String respRecobro = mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 28000,
                                "metodo", "EFECTIVO",
                                "periodoMes", mesActual
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long recobroId = json.readTree(respRecobro).get("id").asLong();

        // Incluso el DELETE legado sobre /api/pagos/{id} realiza baja lógica auditada sin borrar la fila
        mvc.perform(delete("/api/pagos/{id}", recobroId)
                        .header("Authorization", tokenBruno))
                .andExpect(status().isNoContent());

        assertThat(pagoRepository.findById(recobroId)).isPresent();
        assertThat(pagoRepository.findById(recobroId).orElseThrow().getEstadoPago()).isEqualTo(EstadoPago.ANULADO);
    }

    @Test
    void arqueoDiario_desglosaEfectivoTransferenciaSeniasAnulacionesYRecaudacionPorHermano() throws Exception {
        LocalDate fechaArqueo = LocalDate.of(2026, 10, 31);
        String periodo = "2026-10";

        // 1. Bruno cobra $28.000 en Efectivo (PAGADO)
        mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 28000,
                                "metodo", "EFECTIVO",
                                "periodoMes", periodo,
                                "fecha", fechaArqueo.toString()
                        ))))
                .andExpect(status().isCreated());

        // 2. Matías cobra una seña parcial de $12.000 por Transferencia sobre cuota de $30.000 (PARCIAL)
        mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenHermano2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioB.getIdAlumno(),
                                "monto", 30000,
                                "montoAbonado", 12000,
                                "metodo", "TRANSFERENCIA",
                                "periodoMes", periodo,
                                "fecha", fechaArqueo.toString()
                        ))))
                .andExpect(status().isCreated());

        // 3. Bruno carga por error $20.000 de otro mes y lo anula -> no debe inflar la caja
        String respAnular = mvc.perform(post("/api/pagos")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "alumnoId", socioA.getIdAlumno(),
                                "monto", 20000,
                                "metodo", "EFECTIVO",
                                "periodoMes", "2026-11",
                                "fecha", fechaArqueo.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idAnular = json.readTree(respAnular).get("id").asLong();

        mvc.perform(post("/api/pagos/{id}/anular", idAnular)
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "Cobro duplicado por error"))))
                .andExpect(status().isOk());

        String respArqueo = mvc.perform(get("/api/pagos/arqueo")
                        .param("fecha", fechaArqueo.toString())
                        .header("Authorization", tokenBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecaudado").value(40000))
                .andExpect(jsonPath("$.totalEfectivo").value(28000))
                .andExpect(jsonPath("$.totalTransferencia").value(12000))
                .andExpect(jsonPath("$.totalTarjeta").value(0))
                .andExpect(jsonPath("$.cantidadCobros").value(2))
                .andExpect(jsonPath("$.cantidadParciales").value(1))
                .andExpect(jsonPath("$.saldoPendienteParciales").value(18000))
                .andExpect(jsonPath("$.cantidadAnulados").value(1))
                .andExpect(jsonPath("$.montoAnulado").value(20000))
                .andReturn().getResponse().getContentAsString();

        JsonNode operadores = json.readTree(respArqueo).get("porOperador");
        assertThat(operadores).hasSize(2);
        assertThat(operadores.get(0).get("operador").asText()).isEqualTo("Bruno (Dueño)");
        assertThat(operadores.get(0).get("totalRecaudado").decimalValue()).isEqualByComparingTo(new BigDecimal("28000"));
        assertThat(operadores.get(1).get("operador").asText()).isEqualTo("Matías (Dueño)");
        assertThat(operadores.get(1).get("totalRecaudado").decimalValue()).isEqualByComparingTo(new BigDecimal("12000"));
    }
}
