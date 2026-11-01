package com.atlas.backend.Config;

import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Component
@Order(0)
public class AdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public AdminInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${atlas.admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${atlas.admin.bootstrap.email:}") String email,
            @Value("${atlas.admin.bootstrap.password:}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Configure ATLAS_ADMIN_EMAIL para el alta inicial.");
        }
        String identificador = email.trim();
        var existente = usuarios.findByEmailIgnoreCase(identificador);
        if (existente.isPresent()) {
            if (existente.get().getRol() != Rol.ADMIN) {
                throw new IllegalStateException("El identificador del administrador inicial ya pertenece a otra cuenta.");
            }
            log.info("El administrador inicial ya existe. Se conserva su configuracion.");
            return;
        }
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Configure ATLAS_ADMIN_PASSWORD con al menos 12 caracteres y un maximo de 72 bytes UTF-8.");
        }
        Usuario admin = new Usuario();
        admin.setEmail(identificador);
        admin.setPassword(encoder.encode(password));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setFechaCreacion(LocalDate.now());
        usuarios.save(admin);
        log.info("Administrador inicial creado. Desactive el alta inicial y retire sus credenciales de configuracion.");
    }
}
