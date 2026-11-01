package com.atlas.backend;

import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Dto.PagoRequestDto;
import com.atlas.backend.Dto.PagoResponseDto;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.MetodoPago;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Servicio.AlumnoService;
import com.atlas.backend.Servicio.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CuotasEstadoYMultiMesTest {

    @Autowired
    private AlumnoService alumnoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Test
    void opcionA_pagoMesAnterior_alDiaDel1Al10_pendienteDel11Al30_morosoAlPasarElMes() {
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio Opcion A");
        alumno.setActivo(true);
        alumno.setFechaAlta(LocalDate.of(2026, 8, 1));

        Pago pagoAgosto = crearPago("2026-08", LocalDate.of(2026, 8, 5), 1, EstadoPago.PAGADO);
        List<Pago> historial = List.of(pagoAgosto);

        // Durante Agosto -> AL_DIA
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 8, 25)))
                .isEqualTo(EstadoCuenta.AL_DIA);

        // Del 1 al 10 de Septiembre -> sigue AL_DIA (ventana de pago del 1 al 10)
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 9, 1)))
                .isEqualTo(EstadoCuenta.AL_DIA);
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 9, 10)))
                .isEqualTo(EstadoCuenta.AL_DIA);

        // Del 11 al 30 de Septiembre -> PENDIENTE
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 9, 11)))
                .isEqualTo(EstadoCuenta.PENDIENTE);
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 9, 30)))
                .isEqualTo(EstadoCuenta.PENDIENTE);

        // Se pasa del mes de Septiembre sin pagar (arranca Octubre) -> MOROSO
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 10, 1)))
                .isEqualTo(EstadoCuenta.MOROSO);
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 10, 31)))
                .isEqualTo(EstadoCuenta.MOROSO);

        // Tercer mes sin cobertura y más de 60 días sin actividad -> INACTIVO por abandono
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 11, 5)))
                .isEqualTo(EstadoCuenta.INACTIVO);
    }

    @Test
    void promoTrimestral_mantieneAlDiaLosTresMesesYSuVentanaDeRenovacion() {
        Alumno alumno = new Alumno();
        alumno.setNombre("Socio Trimestral");
        alumno.setActivo(true);
        alumno.setFechaAlta(LocalDate.of(2026, 8, 1));

        // Paga el 1 de Agosto un pack Trimestral (cubre 2026-08, 2026-09 y 2026-10)
        Pago pagoTrimestral = crearPago("2026-08", LocalDate.of(2026, 8, 1), 3, EstadoPago.PAGADO);
        List<Pago> historial = List.of(pagoTrimestral);

        // 25 de Octubre (85 días después de la fecha del pago) -> sigue AL_DIA, nunca cae en INACTIVO ni MOROSO
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 10, 25)))
                .isEqualTo(EstadoCuenta.AL_DIA);

        // 8 de Noviembre (ventana 1 al 10 posterior al trimestre) -> AL_DIA
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 11, 8)))
                .isEqualTo(EstadoCuenta.AL_DIA);

        // 12 de Noviembre -> PENDIENTE
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 11, 12)))
                .isEqualTo(EstadoCuenta.PENDIENTE);

        // 1 de Diciembre -> MOROSO
        assertThat(alumnoService.calcularEstadoCuenta(alumno, historial, LocalDate.of(2026, 12, 1)))
                .isEqualTo(EstadoCuenta.MOROSO);
    }

    @Test
    void socioNuevoSinPagos_pendienteMesAlta_morosoMesSiguiente_inactivoAlAbandonar() {
        Alumno nuevo = new Alumno();
        nuevo.setNombre("Nuevo Inscripto");
        nuevo.setActivo(true);
        nuevo.setFechaAlta(LocalDate.of(2026, 9, 15));

        assertThat(alumnoService.calcularEstadoCuenta(nuevo, List.of(), LocalDate.of(2026, 9, 20)))
                .isEqualTo(EstadoCuenta.PENDIENTE);
        assertThat(alumnoService.calcularEstadoCuenta(nuevo, List.of(), LocalDate.of(2026, 10, 5)))
                .isEqualTo(EstadoCuenta.MOROSO);
        assertThat(alumnoService.calcularEstadoCuenta(nuevo, List.of(), LocalDate.of(2026, 11, 20)))
                .isEqualTo(EstadoCuenta.INACTIVO);
    }

    @Test
    void registrarPago_respetaFechaEstadoYCantidadMeses_yListarTodosResuelveEnBatch() {
        Alumno nuevoAlumno = new Alumno();
        nuevoAlumno.setNombre("Martín Trimestral");
        nuevoAlumno.setDni("99887766");
        nuevoAlumno.setActivo(true);
        nuevoAlumno.setFechaAlta(LocalDate.now().minusMonths(2));
        Alumno alumnoGuardado = alumnoRepository.saveAndFlush(nuevoAlumno);
        Long alumnoId = alumnoGuardado.getIdAlumno();

        String periodoHaceDosMeses = LocalDate.now().minusMonths(2).toString().substring(0, 7);
        PagoResponseDto creado = pagoService.registrarPago(new PagoRequestDto(
                alumnoId,
                null,
                new BigDecimal("75000"),
                "TRANSFERENCIA",
                periodoHaceDosMeses,
                LocalDate.now().minusDays(65),
                "PAGADO",
                3,
                "COMP-TRIM-01",
                null,
                null
        ));

        assertThat(creado.cantidadMeses()).isEqualTo(3);
        assertThat(creado.estado()).isEqualTo("PAGADO");

        List<AlumnoResponseDto> todos = alumnoService.listarTodos();
        AlumnoResponseDto encontrado = todos.stream()
                .filter(a -> a.id().equals(alumnoId))
                .findFirst()
                .orElseThrow();

        assertThat(encontrado.estadoCuenta()).isEqualTo(EstadoCuenta.AL_DIA);
    }

    private Pago crearPago(String periodoMes, LocalDate fecha, int cantidadMeses, EstadoPago estado) {
        Pago p = new Pago();
        p.setMonto(new BigDecimal("28000"));
        p.setPeriodoMes(periodoMes);
        p.setFecha(fecha);
        p.setCantidadMeses(cantidadMeses);
        p.setMetodoPago(MetodoPago.EFECTIVO);
        p.setEstadoPago(estado);
        return p;
    }
}
