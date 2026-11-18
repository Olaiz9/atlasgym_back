package com.atlas.backend;

import com.atlas.backend.Dto.PagoCompletarSaldoRequestDto;
import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Servicio.PagoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobro doble: doble toque o los dos titulares cobrando al mismo socio a la vez.
 * Sin @Transactional a propósito: cada cobro corre en su propia transacción, como en producción.
 * El primer titular toma la ficha (o el comprobante) y, mientras no termina, el segundo tiene que
 * quedar esperando; cuando entra, ve el primer cobro y recibe 409 en lugar de duplicarlo.
 */
@SpringBootTest
class CobroConcurrenteTest {

    @Autowired private PagoService pagoService;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JdbcTemplate jdbc;

    private Long alumnoId;
    private final ExecutorService otroTitular = Executors.newSingleThreadExecutor();

    @BeforeEach
    void crearSocio() {
        Alumno socio = new Alumno();
        socio.setNombre("Socio Cobro Concurrente");
        socio.setDni("49111222");
        socio.setActivo(true);
        socio.setFechaAlta(LocalDate.now());
        alumnoId = alumnoRepository.saveAndFlush(socio).getIdAlumno();
    }

    @AfterEach
    void limpiar() {
        otroTitular.shutdownNow();
        jdbc.update("DELETE FROM movimientos_caja WHERE pago_id IN (SELECT id_pago FROM pagos WHERE alumno_id = ?)", alumnoId);
        jdbc.update("DELETE FROM pagos WHERE alumno_id = ?", alumnoId);
        alumnoRepository.deleteById(alumnoId);
    }

    @Test
    void dosCobrosSimultaneosDelMismoMes_elSegundoEsperaYRecibe409() throws Exception {
        String mes = YearMonth.now().toString();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Future<PagoResponseDto> segundo = tx.execute(status -> {
            alumnoRepository.findByIdParaCobro(alumnoId).orElseThrow();
            Future<PagoResponseDto> f = otroTitular.submit(() -> pagoService.registrarPago(cobro(mes, null), "Titular 2"));
            assertThatThrownBy(() -> f.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            pagoService.registrarPago(cobro(mes, null), "Titular 1");
            return f;
        });

        assertThatThrownBy(() -> segundo.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(BusinessException.class)
                .hasMessageContaining("ya tiene un pago acreditado");
        assertThat(pagoRepository.findByAlumnoIdOrderByFechaDesc(alumnoId)).hasSize(1);
    }

    @Test
    void completarSaldoDosVecesALaVez_elSaldoEntraUnaSolaVezALaCaja() throws Exception {
        Long pagoId = pagoService.registrarPago(cobro(YearMonth.now().toString(), new BigDecimal("5000")), "Titular 1").id();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Future<PagoResponseDto> segundo = tx.execute(status -> {
            pagoRepository.findByIdParaActualizar(pagoId).orElseThrow();
            Future<PagoResponseDto> f = otroTitular.submit(
                    () -> pagoService.completarSaldoParcial(pagoId, new PagoCompletarSaldoRequestDto(null, null), "Titular 2"));
            assertThatThrownBy(() -> f.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            pagoService.completarSaldoParcial(pagoId, new PagoCompletarSaldoRequestDto(null, null), "Titular 1");
            return f;
        });

        assertThatThrownBy(() -> segundo.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(BusinessException.class)
                .hasMessageContaining("ya figura abonado");
        // Seña + un solo saldo
        Integer movimientos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_caja WHERE pago_id = ?", Integer.class, pagoId);
        assertThat(movimientos).isEqualTo(2);
    }

    private PagoRequestDto cobro(String periodoMes, BigDecimal montoAbonado) {
        return new PagoRequestDto(alumnoId, null, new BigDecimal("20000"), "EFECTIVO", periodoMes,
                null, null, 1, null, null, null, montoAbonado);
    }
}
