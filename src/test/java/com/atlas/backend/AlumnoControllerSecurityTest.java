package com.atlas.backend;

import com.atlas.backend.Controlador.AlumnoController;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Seguridad.*;
import com.atlas.backend.Servicio.AlumnoService;
import com.atlas.backend.Servicio.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlumnoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AlumnoControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService alumnos;
    @MockitoBean AuthService auth;
    @MockitoBean JwtService jwt;
    @MockitoBean UserDetailsServiceImpl users;

    @Test
    void alumnoConsultaSuPropiaFicha() throws Exception {
        when(auth.resolverAlumnoIdPorEmail("alumno")).thenReturn(10L);
        when(alumnos.buscarPorId(10L)).thenReturn(ficha(10L));
        mvc.perform(get("/api/alumnos/10").with(user("alumno").roles("ALUMNO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void alumnoNoConsultaOtraFicha() throws Exception {
        when(auth.resolverAlumnoIdPorEmail("alumno")).thenReturn(10L);
        mvc.perform(get("/api/alumnos/20").with(user("alumno").roles("ALUMNO")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.id").doesNotExist());
        verifyNoInteractions(alumnos);
    }

    @Test
    void idAjenoInexistenteTampocoRevelaSiExiste() throws Exception {
        when(auth.resolverAlumnoIdPorEmail("alumno")).thenReturn(10L);
        mvc.perform(get("/api/alumnos/999999").with(user("alumno").roles("ALUMNO")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(alumnos);
    }

    @Test
    void administradorPuedeConsultarOtraFicha() throws Exception {
        when(alumnos.buscarPorId(20L)).thenReturn(ficha(20L));
        mvc.perform(get("/api/alumnos/20").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(20));
        verifyNoInteractions(auth);
    }

    @Test
    void sinSesionNoConsultaFichas() throws Exception {
        mvc.perform(get("/api/alumnos/10")).andExpect(status().isForbidden());
        verifyNoInteractions(alumnos, auth);
    }

    @Test
    void otroRolNoConsultaFichas() throws Exception {
        mvc.perform(get("/api/alumnos/10").with(user("otro").roles("OTRO")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(alumnos, auth);
    }

    private AlumnoResponseDto ficha(Long id) {
        return new AlumnoResponseDto(id, "Alumno", null, null, null, null,
                null, null, null, null, true, null, null, null);
    }
}
