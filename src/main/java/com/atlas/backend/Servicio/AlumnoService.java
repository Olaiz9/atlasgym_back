package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AlumnoRequestDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final RutinaRepository rutinaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    @PersistenceContext
    private EntityManager em;

    public AlumnoService(AlumnoRepository alumnoRepository,
                         PlanRepository planRepository,
                         RutinaRepository rutinaRepository,
                         PagoRepository pagoRepository,
                         UsuarioRepository usuarioRepository,
                         PasswordEncoder passwordEncoder) {
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.rutinaRepository = rutinaRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarTodos() {
        return alumnoRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarActivos() {
        return alumnoRepository.findByActivoTrue().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarInactivos() {
        return alumnoRepository.findByActivoFalse().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorId(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorDni(String dni) {
        Alumno alumno = alumnoRepository.findByDni(dni)
                .orElseThrow(() -> new BusinessException("No se encontró ningún alumno con el DNI: " + dni, 404));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> buscarPorNombre(String query) {
        return alumnoRepository.findByNombreContainingIgnoreCase(query).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public AlumnoResponseDto crear(AlumnoRequestDto dto) {
        if (alumnoRepository.existsByDni(dto.dni())) {
            throw new BusinessException("El DNI " + dto.dni() + " ya pertenece a un socio registrado.", 409);
        }

        if (dto.email() != null && !dto.email().isBlank() && alumnoRepository.existsByEmail(dto.email())) {
            throw new BusinessException("El email " + dto.email() + " ya se encuentra registrado.", 409);
        }

        Alumno alumno = new Alumno();
        alumno.setNombre(dto.nombre());
        alumno.setDni(dto.dni());
        alumno.setEmail(dto.email());
        alumno.setCelular(dto.celular());
        alumno.setFechaAlta(LocalDate.now());
        alumno.setActivo(dto.activo() != null ? dto.activo() : true);

        if (dto.planId() != null) {
            Plan plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
            alumno.setPlanAlumno(plan);
        }

        if (dto.rutinaId() != null) {
            Rutina rutina = rutinaRepository.findById(dto.rutinaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rutina", dto.rutinaId()));
            alumno.setRutinaAlumno(rutina);
        }

        // Las credenciales de acceso se crean exclusivamente en Finanzas al registrar el primer pago
        alumno.setUsuario(null);

        Alumno guardado = alumnoRepository.save(alumno);
        return mapToDto(guardado);
    }

    @Transactional
    public AlumnoResponseDto actualizar(Long id, AlumnoRequestDto dto) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));

        if (!alumno.getDni().equals(dto.dni()) && alumnoRepository.existsByDni(dto.dni())) {
            throw new BusinessException("El nuevo DNI " + dto.dni() + " ya está en uso por otro socio.", 409);
        }

        if (dto.email() != null && !dto.email().equals(alumno.getEmail()) && alumnoRepository.existsByEmail(dto.email())) {
            throw new BusinessException("El nuevo email " + dto.email() + " ya está en uso.", 409);
        }

        alumno.setNombre(dto.nombre());
        alumno.setDni(dto.dni());
        alumno.setEmail(dto.email());
        alumno.setCelular(dto.celular());

        if (dto.activo() != null) {
            alumno.setActivo(dto.activo());
        }

        if (alumno.getUsuario() != null) {
            alumno.getUsuario().setCelular(dto.celular());
            alumno.getUsuario().setActivo(alumno.isActivo());
        }

        if (dto.planId() != null) {
            Plan plan = planRepository.findById(dto.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", dto.planId()));
            alumno.setPlanAlumno(plan);
        } else {
            alumno.setPlanAlumno(null);
        }

        if (dto.rutinaId() != null) {
            Rutina rutina = rutinaRepository.findById(dto.rutinaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Rutina", dto.rutinaId()));
            alumno.setRutinaAlumno(rutina);
        } else {
            alumno.setRutinaAlumno(null);
        }

        Alumno actualizado = alumnoRepository.save(alumno);
        return mapToDto(actualizado);
    }

    @Transactional
    public AlumnoResponseDto asignarCredenciales(Long id, String usuarioInput, String passwordInput) {
        if (usuarioInput == null || usuarioInput.isBlank()) {
            throw new BusinessException("El nombre de usuario no puede estar vacío.", 400);
        }
        if (passwordInput == null || passwordInput.isBlank()) {
            throw new BusinessException("La contraseña no puede estar vacía.", 400);
        }

        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));

        String usuarioLimpio = usuarioInput.trim();
        String passwordHash = passwordEncoder.encode(passwordInput.trim());

        var existenteOpt = usuarioRepository.findByEmailIgnoreCase(usuarioLimpio);

        if (alumno.getUsuario() != null) {
            Usuario actual = alumno.getUsuario();
            if (existenteOpt.isPresent() && !existenteOpt.get().getId().equals(actual.getId())) {
                throw new BusinessException("El usuario '" + usuarioLimpio + "' ya está asignado a otra cuenta.", 409);
            }
            actual.setEmail(usuarioLimpio);
            actual.setPassword(passwordHash);
            actual.setActivo(alumno.isActivo());
            actual.setCelular(alumno.getCelular());
            usuarioRepository.save(actual);
        } else {
            if (existenteOpt.isPresent()) {
                Usuario existente = existenteOpt.get();
                var otroAlumno = alumnoRepository.findByUsuarioId(existente.getId());
                if (otroAlumno.isPresent() && !otroAlumno.get().getIdAlumno().equals(alumno.getIdAlumno())) {
                    throw new BusinessException("El usuario '" + usuarioLimpio + "' ya está asignado a otro socio.", 409);
                }
                existente.setPassword(passwordHash);
                existente.setRol(Rol.ALUMNO);
                existente.setActivo(alumno.isActivo());
                existente.setCelular(alumno.getCelular());
                alumno.setUsuario(usuarioRepository.save(existente));
            } else {
                Usuario nuevoUsuario = new Usuario();
                nuevoUsuario.setEmail(usuarioLimpio);
                nuevoUsuario.setPassword(passwordHash);
                nuevoUsuario.setRol(Rol.ALUMNO);
                nuevoUsuario.setActivo(alumno.isActivo());
                nuevoUsuario.setFechaCreacion(LocalDate.now());
                nuevoUsuario.setCelular(alumno.getCelular());
                alumno.setUsuario(usuarioRepository.save(nuevoUsuario));
            }
        }

        Alumno guardado = alumnoRepository.save(alumno);
        return mapToDto(guardado);
    }

    @Transactional
    public void darDeBaja(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));

        // Paso 1: Si está activo, se da de baja lógica y pasa a Inactivos
        if (alumno.isActivo()) {
            alumno.setActivo(false);
            if (alumno.getUsuario() != null) {
                alumno.getUsuario().setActivo(false);
                usuarioRepository.save(alumno.getUsuario());
            }
            List<Pago> pagos = pagoRepository.findByAlumnoIdOrderByFechaDesc(id);
            if (pagos != null && !pagos.isEmpty()) {
                for (Pago p : pagos) {
                    if (p.getAlumnoNombreHistorico() == null || p.getAlumnoNombreHistorico().isBlank()) {
                        p.setAlumnoNombreHistorico(alumno.getNombre());
                    }
                }
                pagoRepository.saveAll(pagos);
            }
            alumnoRepository.save(alumno);
            return;
        }

        // Paso 2: Si ya estaba inactivo, se elimina definitivamente preservando pagos históricos
        eliminarDefinitivamente(id);
    }

    @Transactional
    public void eliminarDefinitivamente(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));

        // 1. Preservar pagos como historicos y desvincular del alumno en la tabla pagos
        List<Pago> pagos = pagoRepository.findByAlumnoIdOrderByFechaDesc(id);
        if (pagos != null && !pagos.isEmpty()) {
            for (Pago p : pagos) {
                if (p.getAlumnoNombreHistorico() == null || p.getAlumnoNombreHistorico().isBlank()) {
                    p.setAlumnoNombreHistorico(alumno.getNombre());
                }
                p.setAlumno(null);
            }
            pagoRepository.saveAllAndFlush(pagos);
        }

        // 2. Eliminar avisos vinculados a este alumno y sus registros de lectura
        em.createNativeQuery("DELETE FROM aviso_leidos WHERE aviso_id IN (SELECT id FROM avisos WHERE alumno_id = :aid)")
                .setParameter("aid", id).executeUpdate();
        em.createNativeQuery("DELETE FROM avisos WHERE alumno_id = :aid")
                .setParameter("aid", id).executeUpdate();

        // 3. Eliminar asistencias
        em.createNativeQuery("DELETE FROM asistencias WHERE alumno_id = :aid")
                .setParameter("aid", id).executeUpdate();

        // 4. Eliminar sesiones de entrenamiento y series
        em.createNativeQuery("DELETE FROM registros_series WHERE sesion_ejercicio_id IN (SELECT id FROM sesiones_ejercicios WHERE sesion_entrenamiento_id IN (SELECT id FROM sesiones_entrenamiento WHERE alumno_id = :aid))")
                .setParameter("aid", id).executeUpdate();
        em.createNativeQuery("DELETE FROM sesiones_ejercicios WHERE sesion_entrenamiento_id IN (SELECT id FROM sesiones_entrenamiento WHERE alumno_id = :aid)")
                .setParameter("aid", id).executeUpdate();
        em.createNativeQuery("DELETE FROM sesiones_entrenamiento WHERE alumno_id = :aid")
                .setParameter("aid", id).executeUpdate();

        // 5. Eliminar alumno de la tabla alumnos
        Usuario usuario = alumno.getUsuario();
        em.createNativeQuery("DELETE FROM alumnos WHERE id_alumno = :aid")
                .setParameter("aid", id).executeUpdate();

        // 6. Eliminar usuario de acceso si existia
        if (usuario != null) {
            em.createNativeQuery("DELETE FROM aviso_leidos WHERE usuario_id = :uid")
                    .setParameter("uid", usuario.getId()).executeUpdate();
            em.createNativeQuery("DELETE FROM usuarios WHERE id = :uid")
                    .setParameter("uid", usuario.getId()).executeUpdate();
        }
    }

    /**
     * Calcula dinámicamente el estado de cuenta del alumno en base a su actividad y pagos.
     * Reglas de ATLAS Gym:
     * - Si activo == false -> INACTIVO.
     * - Si último pago tiene más de 60 días -> INACTIVO.
     * - Si pagó el mes en curso (YYYY-MM) -> AL_DIA.
     * - Si no pagó pero estamos en los primeros 10 días del mes (período de gracia) -> PENDIENTE.
     * - Si no pagó y pasaron más de 10 días -> MOROSO.
     */
    public EstadoCuenta calcularEstadoCuenta(Alumno alumno) {
        if (!alumno.isActivo()) {
            return EstadoCuenta.INACTIVO;
        }

        String periodoActual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Pago> historial = pagoRepository.findByAlumnoIdOrderByFechaDesc(alumno.getIdAlumno());

        if (!historial.isEmpty()) {
            Pago ultimoPago = historial.get(0);
            if (ultimoPago.getEstadoPago() == com.atlas.backend.Modelo.EstadoPago.PENDIENTE) {
                return EstadoCuenta.PENDIENTE;
            }
            if (ultimoPago.getEstadoPago() == com.atlas.backend.Modelo.EstadoPago.VENCIDO) {
                return EstadoCuenta.MOROSO;
            }
            if (ultimoPago.getEstadoPago() == com.atlas.backend.Modelo.EstadoPago.PAGADO) {
                boolean esPeriodoActual = periodoActual.equals(ultimoPago.getPeriodoMes());
                boolean dentroDe30Dias = ultimoPago.getFecha() != null && !ultimoPago.getFecha().isBefore(LocalDate.now().minusDays(31));
                if (esPeriodoActual || dentroDe30Dias) {
                    return EstadoCuenta.AL_DIA;
                }
            }
            if (ultimoPago.getFecha() != null && ultimoPago.getFecha().isBefore(LocalDate.now().minusDays(60))) {
                return EstadoCuenta.INACTIVO;
            }
        } else if (alumno.getFechaAlta() != null && alumno.getFechaAlta().isBefore(LocalDate.now().minusDays(60))) {
            return EstadoCuenta.INACTIVO;
        }

        if (LocalDate.now().getDayOfMonth() <= 10) {
            return EstadoCuenta.PENDIENTE;
        }

        return EstadoCuenta.MOROSO;
    }

    public String generarUsernameBase(String nombreCompleto, String dni) {
        String baseNombre = "socio";
        if (nombreCompleto != null && !nombreCompleto.isBlank()) {
            String normalizado = java.text.Normalizer.normalize(nombreCompleto.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                    .toLowerCase();
            String primerNombre = normalizado.split("\\s+")[0].replaceAll("[^a-z0-9]", "");
            if (!primerNombre.isEmpty()) {
                baseNombre = primerNombre;
            }
        }
        String digitosDni = dni != null ? dni.replaceAll("\\D", "") : "";
        String sufijo = digitosDni.length() >= 3
                ? digitosDni.substring(digitosDni.length() - 3)
                : digitosDni;
        return baseNombre + sufijo;
    }

    public String generarUsernameUnico(String nombreCompleto, String dni, Long usuarioIdActual) {
        String base = generarUsernameBase(nombreCompleto, dni);
        String candidato = base;
        int contador = 1;
        while (true) {
            var existente = usuarioRepository.findByEmailIgnoreCase(candidato);
            if (existente.isEmpty() || (usuarioIdActual != null && existente.get().getId().equals(usuarioIdActual))) {
                return candidato;
            }
            candidato = base + contador;
            contador++;
        }
    }

    public AlumnoResponseDto mapToDto(Alumno alumno) {
        Long planId = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getIdPlan() : null;
        String planNombre = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getNombre() : null;

        Long rutinaId = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getId() : null;
        String rutinaNombre = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getNombre() : null;

        String username = alumno.getUsuario() != null && alumno.getUsuario().getEmail() != null
                ? alumno.getUsuario().getEmail()
                : null;

        EstadoCuenta estadoCuenta = calcularEstadoCuenta(alumno);

        return new AlumnoResponseDto(
                alumno.getIdAlumno(),
                alumno.getNombre(),
                alumno.getDni(),
                username,
                alumno.getEmail(),
                alumno.getCelular(),
                planId,
                planNombre,
                alumno.getFechaAlta(),
                alumno.getUltimaAsistencia(),
                alumno.isActivo(),
                estadoCuenta,
                rutinaId,
                rutinaNombre
        );
    }
}
