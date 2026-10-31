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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final int MAX_INTENTOS_POR_IP_GLOBAL = 25;
    private static final long BLOQUEO_MS = 2 * 60 * 1000L; // 2 minutos

    private record RegistroIntentos(int cantidad, long bloqueadoHastaMs) {}

    private final ConcurrentHashMap<String, RegistroIntentos> intentosPorIdentificador = new ConcurrentHashMap<>();

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
        return login(request, "local");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String identificador = request.email() != null ? request.email().trim() : "";
        String ipNormalizada = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "local";
        String clavePar = ipNormalizada + "|" + identificador.toLowerCase();
        String claveIpGlobal = "ip|" + ipNormalizada;

        verificarBloqueoFuerzaBruta(claveIpGlobal);
        verificarBloqueoFuerzaBruta(clavePar);

        Optional<Usuario> usuarioOpt = resolverUsuarioPorIdentificador(identificador);
        if (usuarioOpt.isEmpty()) {
            registrarIntentoFallido(clavePar, MAX_INTENTOS_FALLIDOS);
            registrarIntentoFallido(claveIpGlobal, MAX_INTENTOS_POR_IP_GLOBAL);
            throw new BusinessException("Credenciales inválidas", 401);
        }

        Usuario usuario = usuarioOpt.get();
        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            registrarIntentoFallido(clavePar, MAX_INTENTOS_FALLIDOS);
            registrarIntentoFallido(claveIpGlobal, MAX_INTENTOS_POR_IP_GLOBAL);
            throw new BusinessException("Credenciales inválidas", 401);
        }

        intentosPorIdentificador.remove(clavePar);

        if (!usuario.isActivo()) {
            throw new BusinessException("La cuenta de usuario se encuentra inactiva", 403);
        }

        Alumno alumnoVinculado = usuario.getAlumno() != null ? usuario.getAlumno()
                : alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumnoVinculado != null && !alumnoVinculado.isActivo()) {
            throw new BusinessException("La cuenta de usuario se encuentra inactiva", 403);
        }

        Long alumnoId = null;
        String nombreMostrar = resolverNombreMostrar(usuario, alumnoVinculado);

        if (alumnoVinculado != null) {
            alumnoId = alumnoVinculado.getIdAlumno();
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
                usuario.getFotoUrl(),
                usuario.isRequiereCambioPassword()
        );
    }

    private String resolverNombreMostrar(Usuario usuario, Alumno alumnoVinculado) {
        if (alumnoVinculado != null && alumnoVinculado.getNombre() != null && !alumnoVinculado.getNombre().isBlank()) {
            return alumnoVinculado.getNombre();
        }
        if (usuario.getNombreMostrar() != null && !usuario.getNombreMostrar().isBlank()) {
            return usuario.getNombreMostrar();
        }
        return usuario.getEmail();
    }

    private void verificarBloqueoFuerzaBruta(String claveIntento) {
        if (claveIntento.isEmpty()) return;
        if (intentosPorIdentificador.size() > 4096) {
            long ahoraLimpieza = System.currentTimeMillis();
            intentosPorIdentificador.entrySet().removeIf(
                    e -> e.getValue().bloqueadoHastaMs() > 0 && ahoraLimpieza >= e.getValue().bloqueadoHastaMs()
            );
        }
        RegistroIntentos registro = intentosPorIdentificador.get(claveIntento);
        if (registro != null && registro.bloqueadoHastaMs() > 0) {
            long ahora = System.currentTimeMillis();
            if (ahora < registro.bloqueadoHastaMs()) {
                long segundosRestantes = Math.max(1, (registro.bloqueadoHastaMs() - ahora) / 1000);
                throw new BusinessException(
                        "Demasiados intentos fallidos desde esta dirección. Por seguridad, aguardá " + segundosRestantes + " segundos antes de volver a intentar.",
                        429
                );
            } else {
                intentosPorIdentificador.remove(claveIntento);
            }
        }
    }

    private void registrarIntentoFallido(String claveIntento, int maxIntentos) {
        if (claveIntento.isEmpty()) return;
        intentosPorIdentificador.compute(claveIntento, (k, actual) -> {
            int nuevosIntentos = (actual == null ? 0 : actual.cantidad()) + 1;
            if (nuevosIntentos >= maxIntentos) {
                return new RegistroIntentos(nuevosIntentos, System.currentTimeMillis() + BLOQUEO_MS);
            }
            return new RegistroIntentos(nuevosIntentos, 0L);
        });
    }

    public void limpiarIntentosFallidos(String claveIntento) {
        if (claveIntento != null) {
            String sufijo = claveIntento.toLowerCase().trim();
            if ("*".equals(sufijo)) {
                intentosPorIdentificador.clear();
                return;
            }
            intentosPorIdentificador.remove(sufijo);
            intentosPorIdentificador.keySet().removeIf(k -> k.endsWith("|" + sufijo));
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse obtenerPerfil(String email) {
        Usuario usuario = resolverUsuarioPorIdentificador(email)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado con email: " + email, 404));

        Alumno alumnoVinculado = usuario.getAlumno() != null ? usuario.getAlumno()
                : alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        Long alumnoId = alumnoVinculado != null ? alumnoVinculado.getIdAlumno() : null;
        String nombreMostrar = resolverNombreMostrar(usuario, alumnoVinculado);
        String rolNombre = usuario.getRol() != null ? usuario.getRol().name() : "ALUMNO";

        return new LoginResponse(
                null,
                usuario.getId(),
                nombreMostrar,
                usuario.getEmail(),
                rolNombre,
                alumnoId,
                usuario.getFotoUrl(),
                usuario.isRequiereCambioPassword()
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
                    String passwordActual = cambios.get("passwordActual");
                    if (passwordActual == null || passwordActual.isBlank()
                            || !passwordEncoder.matches(passwordActual.trim(), usuario.getPassword())) {
                        throw new BusinessException("La contraseña actual ingresada es incorrecta.", 400);
                    }
                    if (nuevaPassword.trim().length() < 6) {
                        throw new BusinessException("La nueva contraseña debe tener al menos 6 caracteres.", 400);
                    }
                    usuario.setPassword(passwordEncoder.encode(nuevaPassword.trim()));
                    usuario.setRequiereCambioPassword(false);
                }
            }
            if (cambios.containsKey("nombre")) {
                String nuevoNombre = cambios.get("nombre");
                if (nuevoNombre != null && !nuevoNombre.isBlank()) {
                    usuario.setNombreMostrar(nuevoNombre.trim());
                }
            }
            if (cambios.containsKey("fotoUrl")) {
                String fotoUrl = cambios.get("fotoUrl");
                if (fotoUrl != null && !fotoUrl.isBlank()) {
                    String limpia = fotoUrl.trim();
                    if (!esUrlImagenSegura(limpia)) {
                        throw new BusinessException("La URL de la foto debe comenzar con https://, http:// o /", 400);
                    }
                    usuario.setFotoUrl(limpia);
                } else {
                    usuario.setFotoUrl(null);
                }
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
        String nombreMostrar = resolverNombreMostrar(usuario, alumnoVinculado);
        String rolNombre = usuario.getRol() != null ? usuario.getRol().name() : "ALUMNO";

        return new LoginResponse(
                null,
                usuario.getId(),
                nombreMostrar,
                usuario.getEmail(),
                rolNombre,
                alumnoId,
                usuario.getFotoUrl(),
                usuario.isRequiereCambioPassword()
        );
    }

    @Transactional(readOnly = true)
    public String resolverEtiquetaOperador(String email) {
        if (email == null || email.isBlank()) return "ADMIN";
        return resolverUsuarioPorIdentificador(email)
                .map(u -> (u.getNombreMostrar() != null && !u.getNombreMostrar().isBlank())
                        ? u.getNombreMostrar().trim()
                        : u.getEmail())
                .orElse(email);
    }

    private boolean esUrlImagenSegura(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("/");
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
