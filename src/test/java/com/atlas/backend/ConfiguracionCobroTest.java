package com.atlas.backend;

import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El alias de transferencia cambia con el tiempo: lo editan los titulares y lo leen los alumnos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfiguracionCobroTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtService jwt;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String tokenAdmin;
    private String tokenAlumno;

    @BeforeEach
    void preparar() {
        Usuario admin = new Usuario();
        admin.setEmail("titular.cobro@atlasgym.com");
        admin.setNombreMostrar("Titular 1");
        admin.setPassword("hash-test");
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin = usuarioRepository.saveAndFlush(admin);
        tokenAdmin = "Bearer " + jwt.generarToken(admin.getId(), admin.getEmail(), "ADMIN", null);

        Usuario alumno = new Usuario();
        alumno.setEmail("socio.cobro");
        alumno.setPassword("hash-test");
        alumno.setRol(Rol.ALUMNO);
        alumno.setActivo(true);
        alumno = usuarioRepository.saveAndFlush(alumno);
        tokenAlumno = "Bearer " + jwt.generarToken(alumno.getId(), alumno.getEmail(), "ALUMNO", null);
    }

    private String cuerpo(String alias, String titular) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("alias", alias);
        m.put("titular", titular);
        return json.writeValueAsString(m);
    }

    @Test
    void titularEditaAliasYAlumnoLoVe() throws Exception {
        mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("  atlas.gym.real  ", "Gimnasio Atlas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("atlas.gym.real"))
                .andExpect(jsonPath("$.actualizadoPor").value("Titular 1"))
                .andExpect(jsonPath("$.actualizadoEn").isNotEmpty());

        mvc.perform(get("/api/configuracion/cobro").header("Authorization", tokenAlumno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("atlas.gym.real"))
                .andExpect(jsonPath("$.titular").value("Gimnasio Atlas"));

        // Cambio de alias: el nuevo reemplaza al anterior
        mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("nuevo-alias-mp", "Otro Titular")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/configuracion/cobro").header("Authorization", tokenAlumno))
                .andExpect(jsonPath("$.alias").value("nuevo-alias-mp"));

        // Vaciar el alias deja de mostrarlo (los alumnos pasan a "abonás en recepción")
        mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").doesNotExist());
    }

    @Test
    void alumnoNoPuedeEditarYSinSesionNoSeLee() throws Exception {
        mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAlumno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("alias.trucho", null)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracion/cobro")).andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaAliasConFormatoInvalido() throws Exception {
        for (String invalido : new String[]{"abc", "alias con espacios", "alias_con_guion_bajo", "123456789012345678901"}) {
            mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(invalido, null)))
                    .andExpect(status().isBadRequest());
        }
        // CBU/CVU de 22 dígitos es válido
        mvc.perform(put("/api/configuracion/cobro").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("0000003100012345678901", "Titular CVU")))
                .andExpect(status().isOk());
    }
}
