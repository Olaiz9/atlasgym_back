package com.atlas.backend.Config;

import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Alta inicial de los titulares (los 2 hermanos dueños). Es opt-in
 * (ATLAS_ADMIN_BOOTSTRAP_ENABLED=true) y no tiene credenciales predeterminadas:
 * si falta la clave o es insegura, la aplicación no arranca.
 * Si la cuenta ya existe no se toca (ni clave, ni estado), así que las variables
 * pueden quedar configuradas sin pisar los cambios que hagan los titulares.
 */
@Component
@Order(0)
public class AdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String email;
    private final String password;
    private final String nombre;
    private final String email2;
    private final String password2;
    private final String nombre2;

    @Autowired
    public AdminInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${atlas.admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${atlas.admin.bootstrap.email:}") String email,
            @Value("${atlas.admin.bootstrap.password:}") String password,
            @Value("${atlas.admin.bootstrap.nombre:}") String nombre,
            @Value("${atlas.admin2.bootstrap.email:}") String email2,
            @Value("${atlas.admin2.bootstrap.password:}") String password2,
            @Value("${atlas.admin2.bootstrap.nombre:}") String nombre2) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
        this.email2 = email2;
        this.password2 = password2;
        this.nombre2 = nombre2;
    }

    /** Solo el titular principal (sin nombre visible ni segundo titular). */
    public AdminInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
            boolean enabled, String email, String password) {
        this(usuarios, encoder, enabled, email, password, null, null, null, null);
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
        crearSiNoExiste(email, password, nombre, "ATLAS_ADMIN_PASSWORD");
        // El segundo titular es opcional: solo se crea si se configuró su identificador.
        if (email2 != null && !email2.isBlank()) {
            crearSiNoExiste(email2, password2, nombre2, "ATLAS_ADMIN2_PASSWORD");
        }
    }

    private void crearSiNoExiste(String emailCrudo, String clave, String nombreMostrar, String variableClave) {
        String identificador = emailCrudo.trim();
        var existente = usuarios.findByEmailIgnoreCase(identificador);
        if (existente.isPresent()) {
            if (existente.get().getRol() != Rol.ADMIN) {
                throw new IllegalStateException("El identificador del administrador inicial ya pertenece a otra cuenta.");
            }
            log.info("El administrador inicial ya existe. Se conserva su configuracion.");
            return;
        }
        if (clave == null || clave.isBlank() || clave.length() < 12
                || clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Configure " + variableClave + " con al menos 12 caracteres y un maximo de 72 bytes UTF-8.");
        }
        Usuario admin = new Usuario();
        admin.setEmail(identificador);
        admin.setPassword(encoder.encode(clave));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setFechaCreacion(LocalDate.now());
        if (nombreMostrar != null && !nombreMostrar.isBlank()) {
            admin.setNombreMostrar(nombreMostrar.trim());
        }
        usuarios.save(admin);
        log.info("Administrador inicial creado. Desactive el alta inicial y retire sus credenciales de configuracion.");
    }
}
