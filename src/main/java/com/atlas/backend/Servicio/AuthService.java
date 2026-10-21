package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.LoginRequest;
import com.atlas.backend.Dto.LoginResponse;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Seguridad.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       AlumnoRepository alumnoRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.alumnoRepository = alumnoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identificador = request.email() != null ? request.email().trim() : "";
        Usuario usuario = resolverUsuarioPorIdentificador(identificador)
                .orElseThrow(() -> new BusinessException("Credenciales inválidas", 401));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BusinessException("Credenciales inválidas", 401);
        }

        if (!usuario.isActivo()) {
            throw new BusinessException("La cuenta de usuario se encuentra inactiva", 403);
        }

        Alumno alumnoVinculado = usuario.getAlumno() != null ? usuario.getAlumno()
                : alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumnoVinculado != null && !alumnoVinculado.isActivo()) {
            throw new BusinessException("La cuenta de usuario se encuentra inactiva", 403);
        }

        Long alumnoId = null;
        String nombreMostrar = usuario.getEmail();

        if (alumnoVinculado != null) {
            alumnoId = alumnoVinculado.getIdAlumno();
            nombreMostrar = alumnoVinculado.getNombre();
        }

        String rolNombre = usuario.getRol() != null ? usuario.getRol().name() : "ALUMNO";
        String token = jwtService.generarToken(usuario.getId(), usuario.getEmail(), rolNombre, alumnoId);

        return new LoginResponse(
                token,
                usuario.getId(),
                nombreMostrar,
                usuario.getEmail(),
                rolNombre,
                alumnoId,
                usuario.getFotoUrl()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse obtenerPerfil(String email) {
        Usuario usuario = resolverUsuarioPorIdentificador(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado con email: " + email, 404));

        Long alumnoId = null;
        String nombreMostrar = usuario.getEmail();

        if (usuario.getAlumno() != null) {
            alumnoId = usuario.getAlumno().getIdAlumno();
            nombreMostrar = usuario.getAlumno().getNombre();
        } else {
            var alumnoOpt = alumnoRepository.findByUsuarioId(usuario.getId());
            if (alumnoOpt.isPresent()) {
                Alumno a = alumnoOpt.get();
                alumnoId = a.getIdAlumno();
                nombreMostrar = a.getNombre();
            }
        }

        String rolNombre = usuario.getRol() != null ? usuario.getRol().name() : "ALUMNO";

        return new LoginResponse(
                null,
                usuario.getId(),
                nombreMostrar,
                usuario.getEmail(),
                rolNombre,
                alumnoId,
                usuario.getFotoUrl()
        );
    }

    @Transactional
    public LoginResponse actualizarPerfil(String identificador, Map<String, String> cambios) {
        Usuario usuario = resolverUsuarioPorIdentificador(identificador)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + identificador, 404));

        if (cambios != null) {
            if (cambios.containsKey("password")) {
                String nuevaPassword = cambios.get("password");
                if (nuevaPassword != null && !nuevaPassword.isBlank()) {
                    if (nuevaPassword.trim().length() < 6) {
                        throw new BusinessException("La contraseña debe tener al menos 6 caracteres.", 400);
                    }
                    usuario.setPassword(passwordEncoder.encode(nuevaPassword.trim()));
                }
            }
            if (cambios.containsKey("fotoUrl")) {
                String fotoUrl = cambios.get("fotoUrl");
                usuario.setFotoUrl(fotoUrl != null && !fotoUrl.isBlank() ? fotoUrl.trim() : null);
            }
        }
        usuarioRepository.save(usuario);

        Alumno alumnoVinculado = usuario.getAlumno() != null ? usuario.getAlumno()
                : alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);

        if (alumnoVinculado != null && cambios != null && cambios.containsKey("celular")) {
            String celular = cambios.get("celular");
            alumnoVinculado.setCelular(celular != null ? celular.trim() : null);
            alumnoRepository.save(alumnoVinculado);
        }

        Long alumnoId = alumnoVinculado != null ? alumnoVinculado.getIdAlumno() : null;
        String nombreMostrar = alumnoVinculado != null ? alumnoVinculado.getNombre() : usuario.getEmail();
        String rolNombre = usuario.getRol() != null ? usuario.getRol().name() : "ALUMNO";

        return new LoginResponse(
                null,
                usuario.getId(),
                nombreMostrar,
                usuario.getEmail(),
                rolNombre,
                alumnoId,
                usuario.getFotoUrl()
        );
    }

    @Transactional(readOnly = true)
    public Long resolverUsuarioIdPorEmail(String email) {
        return resolverUsuarioPorIdentificador(email)
                .map(Usuario::getId)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + email, 404));
    }

    @Transactional(readOnly = true)
    public Long resolverAlumnoIdPorEmail(String email) {
        Usuario usuario = resolverUsuarioPorIdentificador(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + email, 404));

        if (usuario.getAlumno() != null) {
            return usuario.getAlumno().getIdAlumno();
        }

        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(Alumno::getIdAlumno)
                .orElseThrow(() -> new BusinessException("El usuario no tiene una ficha de alumno asociada.", 404));
    }

    private Optional<Usuario> resolverUsuarioPorIdentificador(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        String limpio = identificador.trim();

        // 1. Buscar por nombre de usuario (o email del usuario en tabla usuarios)
        var porUsuario = usuarioRepository.findByEmailIgnoreCase(limpio);
        if (porUsuario.isPresent()) {
            return porUsuario;
        }

        // 2. Buscar por DNI exacto o normalizado (sin puntos ni espacios)
        var porDniExacto = alumnoRepository.findByDni(limpio).map(Alumno::getUsuario);
        if (porDniExacto.isPresent()) {
            return porDniExacto;
        }
        String dniSoloNumeros = limpio.replaceAll("\\D", "");
        if (!dniSoloNumeros.isEmpty() && dniSoloNumeros.length() >= 7) {
            var porDniLimpio = alumnoRepository.findByDni(dniSoloNumeros).map(Alumno::getUsuario);
            if (porDniLimpio.isPresent()) {
                return porDniLimpio;
            }
        }

        // 3. Buscar por email de contacto en la ficha del alumno
        return alumnoRepository.findByEmailIgnoreCase(limpio)
                .map(Alumno::getUsuario);
    }
}
