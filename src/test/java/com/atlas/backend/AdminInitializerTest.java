package com.atlas.backend;

import com.atlas.backend.Config.AdminInitializer;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
class AdminInitializerTest {
    @Autowired UsuarioRepository usuarios;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final String EMAIL = "alta-admin@example.test";
    private static final String CLAVE_TEST = "Solo-para-pruebas-827!";

    @Test
    void deshabilitadoNoCreaCuentasAunqueHayaCredenciales() {
        long antes = usuarios.count();
        new AdminInitializer(usuarios, encoder, false, EMAIL, CLAVE_TEST).run();
        assertThat(usuarios.count()).isEqualTo(antes);
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isEmpty();
    }

    @Test
    void altaExplicitaGuardaHashYNoImprimeCredenciales(CapturedOutput output) {
        new AdminInitializer(usuarios, encoder, true, EMAIL, CLAVE_TEST).run();
        Usuario admin = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo(CLAVE_TEST);
        assertThat(encoder.matches(CLAVE_TEST, admin.getPassword())).isTrue();
        assertThat(output.getAll()).doesNotContain(CLAVE_TEST).doesNotContain(EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "admin123", "corta", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rechazaClaveInseguraSinCrearCuenta(String password) {
        assertThatThrownBy(() -> new AdminInitializer(usuarios, encoder, true, EMAIL, password).run())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ATLAS_ADMIN_PASSWORD");
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isEmpty();
    }

    @Test
    void habilitadoSinIdentificadorFalla() {
        assertThatThrownBy(() -> new AdminInitializer(usuarios, encoder, true, " ", CLAVE_TEST).run())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ATLAS_ADMIN_EMAIL");
    }

    @Test
    void administradorExistenteNoSeDuplicaNiSeReactivaNiCambiaClave() {
        Usuario admin = crearUsuario(Rol.ADMIN);
        String hash = admin.getPassword();
        long antes = usuarios.count();
        new AdminInitializer(usuarios, encoder, true, EMAIL.toUpperCase(), CLAVE_TEST).run();
        usuarios.flush();
        Usuario guardado = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(usuarios.count()).isEqualTo(antes);
        assertThat(guardado.getPassword()).isEqualTo(hash);
        assertThat(guardado.isActivo()).isFalse();
    }

    @Test
    void noConvierteAlumnoExistenteEnAdministrador() {
        Usuario alumno = crearUsuario(Rol.ALUMNO);
        String hash = alumno.getPassword();
        assertThatThrownBy(() -> new AdminInitializer(usuarios, encoder, true, EMAIL, CLAVE_TEST).run())
                .isInstanceOf(IllegalStateException.class);
        assertThat(alumno.getRol()).isEqualTo(Rol.ALUMNO);
        assertThat(alumno.getPassword()).isEqualTo(hash);
    }

    private Usuario crearUsuario(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(EMAIL);
        usuario.setRol(rol);
        usuario.setActivo(false);
        usuario.setPassword(encoder.encode("Anterior-para-pruebas"));
        return usuarios.saveAndFlush(usuario);
    }
}
