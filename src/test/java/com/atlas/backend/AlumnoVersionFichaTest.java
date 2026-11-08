package com.atlas.backend;

import com.atlas.backend.Dto.AlumnoRequestDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Servicio.AlumnoService;
import com.atlas.backend.Servicio.AsistenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los dos titulares pueden tener la misma ficha abierta. Si uno guarda con datos viejos,
 * no tiene que pisar lo que el otro cambió.
 */
@SpringBootTest
@Transactional
class AlumnoVersionFichaTest {

    @Autowired private AlumnoService alumnoService;
    @Autowired private AsistenciaService asistenciaService;
    @Autowired private PlanRepository planRepository;

    private AlumnoRequestDto ficha(String nombre, String dni, String celular, Long planId, Long version) {
        return new AlumnoRequestDto(nombre, dni, null, celular, planId, null, true, version);
    }

    @Test
    void guardarConDatosViejosNoPisaElCambioDelOtroTitular() {
        AlumnoResponseDto creado = alumnoService.crear(ficha("Socia Version", "40999111", "1111", null, null));
        long v0 = creado.version();

        // Titular 1 cambia el celular con la versión que vio
        AlumnoResponseDto deT1 = alumnoService.actualizar(creado.id(), ficha("Socia Version", "40999111", "2222", null, v0));
        assertThat(deT1.version()).isEqualTo(v0 + 1);

        // Titular 2 tenía la ficha abierta desde antes y cambia el nombre: se rechaza
        assertThatThrownBy(() -> alumnoService.actualizar(creado.id(), ficha("Socia Renombrada", "40999111", "1111", null, v0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatusCode()).isEqualTo(409))
                .hasMessageContaining("Otro titular");

        AlumnoResponseDto actual = alumnoService.buscarPorId(creado.id());
        assertThat(actual.celular()).isEqualTo("2222");
        assertThat(actual.nombre()).isEqualTo("Socia Version");

        // Con la ficha actualizada, el titular 2 sí puede guardar
        AlumnoResponseDto deT2 = alumnoService.actualizar(creado.id(), ficha("Socia Renombrada", "40999111", "2222", null, actual.version()));
        assertThat(deT2.nombre()).isEqualTo("Socia Renombrada");
        assertThat(deT2.celular()).isEqualTo("2222");
    }

    @Test
    void elIngresoPorTotemNoCuentaComoCambioDeFicha() {
        Plan plan = planRepository.saveAndFlush(new Plan("Plan Version", "Pase libre", "Libre", new BigDecimal("20000"), true));
        AlumnoResponseDto creado = alumnoService.crear(ficha("Socio Totem Version", "40999222", null, plan.getIdPlan(), null));

        asistenciaService.checkInPorDni("40999222");

        // Un titular que abrió la ficha antes del ingreso puede guardar igual
        AlumnoResponseDto editado = alumnoService.actualizar(creado.id(),
                ficha("Socio Totem Version", "40999222", "3333", plan.getIdPlan(), creado.version()));
        assertThat(editado.celular()).isEqualTo("3333");
    }

    @Test
    void laBajaYLosClientesSinVersionTambienAvanzanLaVersion() {
        AlumnoResponseDto creado = alumnoService.crear(ficha("Socio Baja Version", "40999333", null, null, null));

        AlumnoResponseDto sinVersion = alumnoService.actualizar(creado.id(), ficha("Socio Baja Version", "40999333", "4444", null, null));
        assertThat(sinVersion.version()).isEqualTo(creado.version() + 1);

        alumnoService.darDeBaja(creado.id());
        assertThatThrownBy(() -> alumnoService.actualizar(creado.id(),
                ficha("Socio Baja Version", "40999333", "4444", null, sinVersion.version())))
                .isInstanceOf(BusinessException.class);
        assertThat(alumnoService.buscarPorId(creado.id()).activo()).isFalse();
    }
}
