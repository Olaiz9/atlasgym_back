package com.atlas.backend.Servicio;

import com.atlas.backend.Dto.AlumnoImportItemDto;
import com.atlas.backend.Dto.AlumnoImportResponseDto;
import com.atlas.backend.Dto.AlumnoRequestDto;
import com.atlas.backend.Dto.AlumnoResponseDto;
import com.atlas.backend.Excepciones.BusinessException;
import com.atlas.backend.Excepciones.ResourceNotFoundException;
import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.MetodoPago;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlumnoService {

    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final RutinaRepository rutinaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

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
        return mapListToDto(alumnoRepository.findAllWithRelaciones());
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarActivos() {
        return mapListToDto(alumnoRepository.findActivosWithRelaciones());
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> listarInactivos() {
        return mapListToDto(alumnoRepository.findInactivosWithRelaciones());
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorId(Long id) {
        Alumno alumno = alumnoRepository.findByIdWithRelaciones(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public AlumnoResponseDto buscarPorDni(String dni) {
        Alumno alumno = alumnoRepository.findByDniWithRelaciones(dni)
                .orElseThrow(() -> new BusinessException("No se encontró ningún alumno con el DNI: " + dni, 404));
        return mapToDto(alumno);
    }

    @Transactional(readOnly = true)
    public List<AlumnoResponseDto> buscarPorNombre(String query) {
        return mapListToDto(alumnoRepository.findByNombreContainingIgnoreCaseWithRelaciones(query));
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

        // Generar automáticamente el nombre de usuario y cuenta de acceso para el nuevo inscripto
        String usernameGenerado = generarUsernameUnico(dto.nombre(), dto.dni(), null);
        String dniSoloNumeros = dto.dni().replaceAll("\\D", "");
        String passwordInicial = dniSoloNumeros.isEmpty() ? "alumno123" : dniSoloNumeros;

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(usernameGenerado);
        nuevoUsuario.setPassword(passwordEncoder.encode(passwordInicial));
        nuevoUsuario.setRol(Rol.ALUMNO);
        nuevoUsuario.setActivo(alumno.isActivo());
        nuevoUsuario.setFechaCreacion(LocalDate.now());
        nuevoUsuario.setCelular(alumno.getCelular());
        nuevoUsuario.setRequiereCambioPassword(true);
        alumno.setUsuario(usuarioRepository.save(nuevoUsuario));

        Alumno guardado = alumnoRepository.save(alumno);
        return mapToDto(guardado, List.of());
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
        String claveLimpia = passwordInput.trim();
        String passwordHash = passwordEncoder.encode(claveLimpia);
        String dniSoloNumeros = alumno.getDni() != null ? alumno.getDni().replaceAll("\\D", "") : "";
        boolean esClavePorDni = !dniSoloNumeros.isEmpty() && dniSoloNumeros.equals(claveLimpia);

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
            actual.setRequiereCambioPassword(esClavePorDni);
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
                existente.setRequiereCambioPassword(esClavePorDni);
                alumno.setUsuario(usuarioRepository.save(existente));
            } else {
                Usuario nuevoUsuario = new Usuario();
                nuevoUsuario.setEmail(usuarioLimpio);
                nuevoUsuario.setPassword(passwordHash);
                nuevoUsuario.setRol(Rol.ALUMNO);
                nuevoUsuario.setActivo(alumno.isActivo());
                nuevoUsuario.setFechaCreacion(LocalDate.now());
                nuevoUsuario.setCelular(alumno.getCelular());
                nuevoUsuario.setRequiereCambioPassword(esClavePorDni);
                alumno.setUsuario(usuarioRepository.save(nuevoUsuario));
            }
        }

        Alumno guardado = alumnoRepository.save(alumno);
        return mapToDto(guardado);
    }

    /**
     * Blanquea en 1 clic la contraseña del socio a su DNI numérico y activa el cambio obligatorio
     * de clave en su próximo inicio de sesión.
     */
    @Transactional
    public AlumnoResponseDto blanquearClaveADni(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        String dniSoloNumeros = alumno.getDni() != null ? alumno.getDni().replaceAll("\\D", "") : "";
        String claveReset = dniSoloNumeros.isEmpty() ? "alumno123" : dniSoloNumeros;
        String username = alumno.getUsuario() != null && alumno.getUsuario().getEmail() != null
                ? alumno.getUsuario().getEmail()
                : generarUsernameUnico(alumno.getNombre(), alumno.getDni(), null);

        AlumnoResponseDto actualizado = asignarCredenciales(id, username, claveReset);
        Alumno recargado = alumnoRepository.findById(id).orElse(alumno);
        if (recargado.getUsuario() != null) {
            recargado.getUsuario().setRequiereCambioPassword(true);
            usuarioRepository.save(recargado.getUsuario());
        }
        return actualizado;
    }

    @Transactional
    public void darDeBaja(Long id) {
        Alumno alumno = alumnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alumno", id));
        alumno.darDeBaja();
        if (alumno.getUsuario() != null) {
            alumno.getUsuario().setActivo(false);
        }
        alumnoRepository.save(alumno);
    }

    /**
     * Calcula dinámicamente el estado de cuenta del alumno según la política oficial (Opción A):
     * - Baja administrativa (activo == false) -> INACTIVO.
     * - Si tiene cubierto el mes en curso o meses futuros (soporta planes multi-mes) -> AL_DIA.
     * - Si cubrió hasta el mes pasado:
     *     * Del día 1 al 10 del mes actual -> AL_DIA (ventana de pago del 1 al 10).
     *     * Del día 11 a fin de mes -> PENDIENTE.
     * - Si se pasó el mes entero sin abonar (debe el mes anterior completo) -> MOROSO.
     * - Si acumula >= 3 meses sin cobertura pagada y > 60 días sin actividad de pago -> INACTIVO.
     */
    public EstadoCuenta calcularEstadoCuenta(Alumno alumno) {
        return calcularEstadoCuenta(alumno, null, LocalDate.now());
    }

    public EstadoCuenta calcularEstadoCuenta(Alumno alumno, List<Pago> historialPreCargado, LocalDate fechaReferencia) {
        if (alumno == null || !alumno.isActivo()) {
            return EstadoCuenta.INACTIVO;
        }

        LocalDate ref = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        YearMonth ymActual = YearMonth.from(ref);
        int dia = ref.getDayOfMonth();

        List<Pago> historialCrudo = historialPreCargado != null
                ? historialPreCargado
                : pagoRepository.findByAlumnoIdOrderByFechaDesc(alumno.getIdAlumno());

        List<Pago> historial = historialCrudo == null
                ? List.of()
                : historialCrudo.stream()
                        .filter(p -> p.getEstadoPago() != EstadoPago.ANULADO)
                        .toList();

        // 1. Socio sin pagos activos registrados aún (excluyendo anulaciones)
        if (historial.isEmpty()) {
            if (alumno.getFechaAlta() != null) {
                YearMonth ymAlta = YearMonth.from(alumno.getFechaAlta());
                long mesesDesdeAlta = ChronoUnit.MONTHS.between(ymAlta, ymActual);
                long diasDesdeAlta = ChronoUnit.DAYS.between(alumno.getFechaAlta(), ref);
                if (mesesDesdeAlta >= 2 || diasDesdeAlta > 60) {
                    return EstadoCuenta.INACTIVO;
                }
                if (mesesDesdeAlta >= 1) {
                    return EstadoCuenta.MOROSO;
                }
            }
            return EstadoCuenta.PENDIENTE;
        }

        // 2. Evaluar cobertura máxima por estado y última fecha de movimiento
        YearMonth maxPeriodoPagado = null;
        YearMonth maxPeriodoPendiente = null;
        YearMonth maxPeriodoVencido = null;
        LocalDate ultimaFechaMovimiento = null;

        for (Pago p : historial) {
            if (p.getFecha() != null && (ultimaFechaMovimiento == null || p.getFecha().isAfter(ultimaFechaMovimiento))) {
                ultimaFechaMovimiento = p.getFecha();
            }
            YearMonth finCubierto = obtenerPeriodoFinCubierto(p);
            if (finCubierto == null) continue;

            EstadoPago est = p.getEstadoPago() != null ? p.getEstadoPago() : EstadoPago.PAGADO;
            if (est == EstadoPago.PAGADO) {
                if (maxPeriodoPagado == null || finCubierto.isAfter(maxPeriodoPagado)) {
                    maxPeriodoPagado = finCubierto;
                }
            } else if (est == EstadoPago.PENDIENTE || est == EstadoPago.PARCIAL) {
                if (maxPeriodoPendiente == null || finCubierto.isAfter(maxPeriodoPendiente)) {
                    maxPeriodoPendiente = finCubierto;
                }
            } else if (est == EstadoPago.VENCIDO) {
                if (maxPeriodoVencido == null || finCubierto.isAfter(maxPeriodoVencido)) {
                    maxPeriodoVencido = finCubierto;
                }
            }
        }

        // 3. Abandono (> 60 días sin actividad de pagos y >= 3 meses sin cobertura pagada)
        boolean tieneMovimientoReciente = ultimaFechaMovimiento != null
                && ChronoUnit.DAYS.between(ultimaFechaMovimiento, ref) <= 60;

        if (maxPeriodoPagado != null) {
            long mesesSinCobertura = ChronoUnit.MONTHS.between(maxPeriodoPagado, ymActual);
            if (mesesSinCobertura >= 3 && !tieneMovimientoReciente) {
                return EstadoCuenta.INACTIVO;
            }
        } else if (!tieneMovimientoReciente) {
            return EstadoCuenta.INACTIVO;
        }

        // 4. Cuota explícitamente marcada como VENCIDO no cubierta por un pago posterior
        if (maxPeriodoVencido != null && (maxPeriodoPagado == null || maxPeriodoVencido.isAfter(maxPeriodoPagado))) {
            return EstadoCuenta.MOROSO;
        }

        // 5. Cuota del mes actual (o posterior) explícitamente en estado PENDIENTE aún no cubierta
        if (maxPeriodoPendiente != null
                && !maxPeriodoPendiente.isBefore(ymActual)
                && (maxPeriodoPagado == null || maxPeriodoPagado.isBefore(ymActual))) {
            return EstadoCuenta.PENDIENTE;
        }

        // 6. Regla Opción A sobre cobertura PAGADA
        if (maxPeriodoPagado != null) {
            long diffMeses = ChronoUnit.MONTHS.between(maxPeriodoPagado, ymActual);
            if (diffMeses <= 0) {
                return EstadoCuenta.AL_DIA;
            }
            if (diffMeses == 1) {
                return (dia <= 10) ? EstadoCuenta.AL_DIA : EstadoCuenta.PENDIENTE;
            }
            return EstadoCuenta.MOROSO;
        }

        // 7. Socio sin pagos PAGADO pero con cuotas PENDIENTE recientes
        if (maxPeriodoPendiente != null) {
            long diffPendiente = ChronoUnit.MONTHS.between(maxPeriodoPendiente, ymActual);
            return (diffPendiente <= 0) ? EstadoCuenta.PENDIENTE : EstadoCuenta.MOROSO;
        }

        return EstadoCuenta.MOROSO;
    }

    public static YearMonth obtenerPeriodoFinCubierto(Pago p) {
        if (p == null) return null;
        YearMonth inicio = null;
        if (p.getPeriodoMes() != null && !p.getPeriodoMes().isBlank()) {
            try {
                inicio = YearMonth.parse(p.getPeriodoMes().trim());
            } catch (DateTimeParseException ignored) {
            }
        }
        if (inicio == null && p.getFecha() != null) {
            inicio = YearMonth.from(p.getFecha());
        }
        if (inicio == null) return null;

        int meses = p.getCantidadMeses();
        return meses > 1 ? inicio.plusMonths(meses - 1L) : inicio;
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

    private List<AlumnoResponseDto> mapListToDto(List<Alumno> alumnos) {
        if (alumnos == null || alumnos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = alumnos.stream()
                .map(Alumno::getIdAlumno)
                .toList();

        List<Pago> todosLosPagos = pagoRepository.findByAlumnoIdsOrderByFechaDesc(ids);
        Map<Long, List<Pago>> pagosPorAlumno = new HashMap<>();
        for (Pago p : todosLosPagos) {
            if (p.getAlumno() != null && p.getAlumno().getIdAlumno() != null) {
                pagosPorAlumno
                        .computeIfAbsent(p.getAlumno().getIdAlumno(), k -> new ArrayList<>())
                        .add(p);
            }
        }

        LocalDate hoy = LocalDate.now();
        return alumnos.stream()
                .map(a -> mapToDto(a, pagosPorAlumno.getOrDefault(a.getIdAlumno(), List.of()), hoy))
                .toList();
    }

    public AlumnoResponseDto mapToDto(Alumno alumno) {
        List<Pago> pagos = pagoRepository.findByAlumnoIdOrderByFechaDesc(alumno.getIdAlumno());
        return mapToDto(alumno, pagos, LocalDate.now());
    }

    private AlumnoResponseDto mapToDto(Alumno alumno, List<Pago> pagos) {
        return mapToDto(alumno, pagos, LocalDate.now());
    }

    private AlumnoResponseDto mapToDto(Alumno alumno, List<Pago> pagos, LocalDate fechaReferencia) {
        Long planId = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getIdPlan() : null;
        String planNombre = alumno.getPlanAlumno() != null ? alumno.getPlanAlumno().getNombre() : null;

        Long rutinaId = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getId() : null;
        String rutinaNombre = alumno.getRutinaAlumno() != null ? alumno.getRutinaAlumno().getNombre() : null;

        String username = alumno.getUsuario() != null && alumno.getUsuario().getEmail() != null
                ? alumno.getUsuario().getEmail()
                : generarUsernameBase(alumno.getNombre(), alumno.getDni());

        EstadoCuenta estadoCuenta = calcularEstadoCuenta(alumno, pagos, fechaReferencia);

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

    /**
     * Importador masivo transaccional ("Día Cero") para migrar el padrón histórico desde Excel / CSV.
     * Crea o actualiza fichas por DNI, habilita acceso inicial con DNI + cambio obligatorio de clave
     * y opcionalmente siembra el último período mensual abonado para que cada socio despierte con su
     * EstadoCuenta real (AL_DIA, PENDIENTE o MOROSO).
     */
    @Transactional
    public AlumnoImportResponseDto importarPadronMasivo(List<AlumnoImportItemDto> filas, String operador) {
        if (filas == null || filas.isEmpty()) {
            throw new BusinessException("El archivo de importación no contiene filas válidas.");
        }
        if (filas.size() > 2000) {
            throw new BusinessException("El lote supera el máximo de 2.000 socios por importación.");
        }

        List<Plan> planesDisponibles = planRepository.findAll();
        Plan planDefault = planesDisponibles.stream()
                .filter(Plan::isActivo)
                .findFirst()
                .orElse(planesDisponibles.isEmpty() ? null : planesDisponibles.get(0));

        int procesados = 0;
        int creados = 0;
        int actualizados = 0;
        int pagosIniciales = 0;
        int omitidos = 0;
        List<String> advertencias = new ArrayList<>();
        String opEtiqueta = (operador != null && !operador.isBlank()) ? operador.trim() : "Importación CSV";

        for (int i = 0; i < filas.size(); i++) {
            AlumnoImportItemDto item = filas.get(i);
            int nroFila = i + 1;
            if (item == null) {
                omitidos++;
                continue;
            }

            String nombre = item.nombre() != null ? item.nombre().trim() : "";
            String dni = item.dni() != null ? item.dni().replaceAll("\\D", "") : "";

            if (nombre.length() < 2 || dni.length() < 6 || dni.length() > 10) {
                omitidos++;
                advertencias.add("Fila " + nroFila + ": omitida por nombre o DNI inválido (" + nombre + " / " + (item.dni() != null ? item.dni() : "-") + ").");
                continue;
            }

            procesados++;
            String celular = item.celular() != null && !item.celular().isBlank() ? item.celular().trim() : null;
            String email = item.email() != null && !item.email().isBlank() ? item.email().trim() : null;
            Plan planResuelto = resolverPlanPorNombre(item.planNombre(), planesDisponibles, planDefault);

            var existenteOpt = alumnoRepository.findByDni(dni);
            Alumno alumno;
            if (existenteOpt.isPresent()) {
                alumno = existenteOpt.get();
                alumno.setNombre(nombre);
                if (celular != null) {
                    alumno.setCelular(celular);
                }
                if (email != null) {
                    boolean emailOcupadoPorOtro = !email.equalsIgnoreCase(alumno.getEmail())
                            && alumnoRepository.existsByEmail(email);
                    if (!emailOcupadoPorOtro) {
                        alumno.setEmail(email);
                    } else {
                        advertencias.add("Fila " + nroFila + ": email '" + email + "' ya pertenece a otro socio, se conservó el anterior.");
                    }
                }
                if (planResuelto != null) {
                    alumno.setPlanAlumno(planResuelto);
                }
                alumno.setActivo(true);
                if (alumno.getUsuario() != null) {
                    alumno.getUsuario().setActivo(true);
                    if (celular != null) {
                        alumno.getUsuario().setCelular(celular);
                    }
                    usuarioRepository.save(alumno.getUsuario());
                } else {
                    alumno.setUsuario(crearUsuarioInicialSocio(nombre, dni, celular));
                }
                alumno = alumnoRepository.saveAndFlush(alumno);
                actualizados++;
            } else {
                alumno = new Alumno();
                alumno.setNombre(nombre);
                alumno.setDni(dni);
                if (email != null && !alumnoRepository.existsByEmail(email)) {
                    alumno.setEmail(email);
                } else if (email != null) {
                    advertencias.add("Fila " + nroFila + ": email '" + email + "' duplicado, se dio de alta sin email secundario.");
                }
                alumno.setCelular(celular);
                alumno.setFechaAlta(LocalDate.now());
                alumno.setActivo(true);
                alumno.setPlanAlumno(planResuelto);
                alumno.setUsuario(crearUsuarioInicialSocio(nombre, dni, celular));
                alumno = alumnoRepository.saveAndFlush(alumno);
                creados++;
            }

            YearMonth ymUltimoPago = parsearPeriodoImportacion(item.ultimoPeriodoPago());
            if (item.ultimoPeriodoPago() != null && !item.ultimoPeriodoPago().isBlank() && ymUltimoPago == null) {
                advertencias.add("Fila " + nroFila + " (" + nombre + "): período '" + item.ultimoPeriodoPago() + "' inválido (usar YYYY-MM).");
            }

            if (ymUltimoPago != null) {
                boolean yaTienePeriodo = pagoRepository.findByAlumnoIdOrderByFechaDesc(alumno.getIdAlumno())
                        .stream()
                        .filter(p -> p.getEstadoPago() != EstadoPago.ANULADO)
                        .anyMatch(p -> {
                            YearMonth fin = obtenerPeriodoFinCubierto(p);
                            return fin != null && !fin.isBefore(ymUltimoPago);
                        });

                if (!yaTienePeriodo) {
                    java.math.BigDecimal montoPago = (item.montoUltimoPago() != null && item.montoUltimoPago().compareTo(java.math.BigDecimal.ZERO) > 0)
                            ? item.montoUltimoPago()
                            : (planResuelto != null && planResuelto.getPrecio() != null && planResuelto.getPrecio().compareTo(java.math.BigDecimal.ZERO) > 0
                                    ? planResuelto.getPrecio()
                                    : new java.math.BigDecimal("25000"));

                    LocalDate fechaPago = ymUltimoPago.equals(YearMonth.now())
                            ? LocalDate.now()
                            : ymUltimoPago.atDay(Math.min(5, ymUltimoPago.lengthOfMonth()));

                    // Si el pago inicial corresponde a >= 2 meses atrás, ajustar fechaAlta para coherencia contable
                    if (alumno.getFechaAlta() != null && fechaPago.isBefore(alumno.getFechaAlta())) {
                        alumno.setFechaAlta(fechaPago);
                        alumnoRepository.save(alumno);
                    }

                    Pago pagoInicial = new Pago();
                    pagoInicial.setAlumno(alumno);
                    pagoInicial.setPlanPago(planResuelto);
                    pagoInicial.setMonto(montoPago);
                    pagoInicial.setMontoAbonado(montoPago);
                    pagoInicial.setFecha(fechaPago);
                    pagoInicial.setPeriodoMes(ymUltimoPago.toString());
                    pagoInicial.setCantidadMeses(1);
                    pagoInicial.setAlumnoNombreHistorico(alumno.getNombre());
                    pagoInicial.setMetodoPago(parsearMetodoImportacion(item.metodoPago()));
                    pagoInicial.setEstadoPago(EstadoPago.PAGADO);
                    pagoInicial.setComprobante("Migración Día Cero");
                    pagoInicial.setRegistradoPor(opEtiqueta);
                    pagoRepository.saveAndFlush(pagoInicial);
                    pagosIniciales++;
                }
            }
        }

        return new AlumnoImportResponseDto(
                procesados,
                creados,
                actualizados,
                pagosIniciales,
                omitidos,
                advertencias
        );
    }

    private Usuario crearUsuarioInicialSocio(String nombre, String dni, String celular) {
        String username = generarUsernameUnico(nombre, dni, null);
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setEmail(username);
        nuevoUsuario.setPassword(passwordEncoder.encode(dni));
        nuevoUsuario.setRol(Rol.ALUMNO);
        nuevoUsuario.setActivo(true);
        nuevoUsuario.setFechaCreacion(LocalDate.now());
        nuevoUsuario.setCelular(celular);
        nuevoUsuario.setRequiereCambioPassword(true);
        return usuarioRepository.save(nuevoUsuario);
    }

    private Plan resolverPlanPorNombre(String nombreInput, List<Plan> planes, Plan planDefault) {
        if (nombreInput == null || nombreInput.isBlank() || planes.isEmpty()) {
            return planDefault;
        }
        String buscado = normalizarTextoComparacion(nombreInput);
        for (Plan p : planes) {
            if (normalizarTextoComparacion(p.getNombre()).equals(buscado)) {
                return p;
            }
        }
        for (Plan p : planes) {
            String cand = normalizarTextoComparacion(p.getNombre());
            if (cand.contains(buscado) || buscado.contains(cand)) {
                return p;
            }
        }
        return planDefault;
    }

    private String normalizarTextoComparacion(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase();
    }

    private YearMonth parsearPeriodoImportacion(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String limpio = raw.trim();
        try {
            if (limpio.matches("^\\d{4}-\\d{2}$")) {
                return YearMonth.parse(limpio);
            }
            if (limpio.matches("^\\d{4}/\\d{2}$")) {
                return YearMonth.parse(limpio.replace('/', '-'));
            }
            if (limpio.matches("^\\d{1,2}/\\d{4}$")) {
                String[] partes = limpio.split("/");
                int mes = Integer.parseInt(partes[0]);
                int anio = Integer.parseInt(partes[1]);
                return YearMonth.of(anio, mes);
            }
            if (limpio.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                return YearMonth.from(LocalDate.parse(limpio));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private MetodoPago parsearMetodoImportacion(String metodoRaw) {
        if (metodoRaw == null) return MetodoPago.EFECTIVO;
        String m = metodoRaw.toUpperCase().trim();
        if (m.contains("TRANSF") || m.contains("MERCADO") || m.equals("MP")) return MetodoPago.TRANSFERENCIA;
        if (m.contains("TARJ") || m.contains("DEBIT") || m.contains("CREDIT")) return MetodoPago.TARJETA;
        return MetodoPago.EFECTIVO;
    }
}
