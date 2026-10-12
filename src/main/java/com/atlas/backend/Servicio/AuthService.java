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
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas", 401));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BusinessException("Credenciales inválidas", 401);
        }

        if (!usuario.isActivo()) {
            throw new BusinessException("La cuenta de usuario se encuentra inactiva", 403);
        }

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
        Usuario usuario = usuarioRepository.findByEmail(email)
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

    @Transactional(readOnly = true)
    public Long resolverUsuarioIdPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .map(Usuario::getId)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + email, 404));
    }

    @Transactional(readOnly = true)
    public Long resolverAlumnoIdPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + email, 404));

        if (usuario.getAlumno() != null) {
            return usuario.getAlumno().getIdAlumno();
        }

        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(Alumno::getIdAlumno)
                .orElseThrow(() -> new BusinessException("El usuario no tiene una ficha de alumno asociada.", 404));
    }
}
