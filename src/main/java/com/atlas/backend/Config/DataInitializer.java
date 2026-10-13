package com.atlas.backend.Config;

import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Asistencia;
import com.atlas.backend.Modelo.Aviso;
import com.atlas.backend.Modelo.CategoriaAviso;
import com.atlas.backend.Modelo.DiaRutina;
import com.atlas.backend.Modelo.Ejercicio;
import com.atlas.backend.Modelo.EstadoCuenta;
import com.atlas.backend.Modelo.EstadoPago;
import com.atlas.backend.Modelo.FormatoVideo;
import com.atlas.backend.Modelo.GrupoMuscular;
import com.atlas.backend.Modelo.MetodoAsistencia;
import com.atlas.backend.Modelo.MetodoPago;
import com.atlas.backend.Modelo.ObjetivoRutina;
import com.atlas.backend.Modelo.Pago;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.TipoSerieEjercicio;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Modelo.VideoTecnica;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AsistenciaRepository;
import com.atlas.backend.Repositorio.AvisoRepository;
import com.atlas.backend.Repositorio.PagoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import com.atlas.backend.Repositorio.VideoTecnicaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${atlas.seed.enabled:true}")
    private boolean seedEnabled;

    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final RutinaRepository rutinaRepository;
    private final AvisoRepository avisoRepository;
    private final PagoRepository pagoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final VideoTecnicaRepository videoTecnicaRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           AlumnoRepository alumnoRepository,
                           PlanRepository planRepository,
                           RutinaRepository rutinaRepository,
                           AvisoRepository avisoRepository,
                           PagoRepository pagoRepository,
                           AsistenciaRepository asistenciaRepository,
                           VideoTecnicaRepository videoTecnicaRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.rutinaRepository = rutinaRepository;
        this.avisoRepository = avisoRepository;
        this.pagoRepository = pagoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.videoTecnicaRepository = videoTecnicaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Asegurar que exista el usuario Administrador aún en producción si la tabla usuarios está vacía
        if (!usuarioRepository.existsByEmail("admin@atlasgym.com")) {
            Usuario admin = new Usuario();
            admin.setEmail("admin@atlasgym.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRol(Rol.ADMIN);
            admin.setActivo(true);
            admin.setFechaCreacion(LocalDate.now());
            admin.setCelular("+54 9 11 9876-5432");
            usuarioRepository.save(admin);
            log.info("DataInitializer: Creado usuario ADMIN (admin@atlasgym.com / admin123)");
        }

        if (!seedEnabled) {
            log.info("DataInitializer: atlas.seed.enabled=false (modo producción). Se omiten datos de demostración.");
            return;
        }

        String periodoActual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 2. Planes de membresía base
        Plan planMusculacion;
        Plan planFull;
        Plan planEstudiante;
        List<Plan> planesExistentes = planRepository.findAll();
        if (planesExistentes.isEmpty()) {
            planMusculacion = new Plan();
            planMusculacion.setNombre("Plan Musculación Libre");
            planMusculacion.setPrecio(new BigDecimal("22000.00"));
            planMusculacion.setDiaPorSemana("5 días");
            planMusculacion.setDescripcion("Acceso total e ilimitado a la sala de musculación de lunes a viernes.");
            planMusculacion.setActivo(true);
            planMusculacion = planRepository.save(planMusculacion);

            planFull = new Plan();
            planFull.setNombre("Plan Full Pass");
            planFull.setPrecio(new BigDecimal("28000.00"));
            planFull.setDiaPorSemana("6 días");
            planFull.setDescripcion("Musculación + Clases + Seguimiento de entrenamiento.");
            planFull.setActivo(true);
            planFull = planRepository.save(planFull);

            planEstudiante = new Plan();
            planEstudiante.setNombre("Plan Estudiante");
            planEstudiante.setPrecio(new BigDecimal("17500.00"));
            planEstudiante.setDiaPorSemana("3 días");
            planEstudiante.setDescripcion("3 días por semana con descuento universitario.");
            planEstudiante.setActivo(true);
            planEstudiante = planRepository.save(planEstudiante);
            log.info("DataInitializer: Creados 3 planes de membresía base.");
        } else {
            planMusculacion = planesExistentes.get(0);
            planFull = planesExistentes.size() > 1 ? planesExistentes.get(1) : planMusculacion;
            planEstudiante = planesExistentes.size() > 2 ? planesExistentes.get(2) : planMusculacion;
        }

        // 3. Rutina base de prueba
        Rutina rutinaBase;
        List<Rutina> rutinasExistentes = rutinaRepository.findAll();
        if (rutinasExistentes.isEmpty()) {
            rutinaBase = new Rutina();
            rutinaBase.setNombre("Rutina Hipertrofia A/B");
            rutinaBase.setDescripcion("Rutina balanceada de fuerza e hipertrofia para desarrollo muscular.");
            rutinaBase.setObjetivo(ObjetivoRutina.HIPERTROFIA);
            rutinaBase.setEsGenerica(true);
            rutinaBase.setFechaCreacion(LocalDate.now());

            DiaRutina diaTorso = new DiaRutina();
            diaTorso.setNombre("Día 1 - Torso y empuje");
            diaTorso.setOrden(1);

            Ejercicio ejBanca = new Ejercicio();
            ejBanca.setNombreEjercicio("Press de Banca Plano con Barra");
            ejBanca.setSerie(4);
            ejBanca.setRepeticiones("8-10");
            ejBanca.setDescansoSegundos("90");
            ejBanca.setNotas("Mantener escápulas retraídas y RPE 8.");
            ejBanca.setOrden(1);
            ejBanca.setTipoSerie(TipoSerieEjercicio.NORMAL);
            diaTorso.addEjercicio(ejBanca);

            Ejercicio ejRemo = new Ejercicio();
            ejRemo.setNombreEjercicio("Remo con Barra T");
            ejRemo.setSerie(4);
            ejRemo.setRepeticiones("10-12");
            ejRemo.setDescansoSegundos("75");
            ejRemo.setNotas("Espalda neutra, tirar con los codos.");
            ejRemo.setOrden(2);
            ejRemo.setTipoSerie(TipoSerieEjercicio.BI_SERIE);
            diaTorso.addEjercicio(ejRemo);

            rutinaBase.addDiasRutina(diaTorso);

            DiaRutina diaPierna = new DiaRutina();
            diaPierna.setNombre("Día 2 - Pierna y core");
            diaPierna.setOrden(2);

            Ejercicio ejSentadilla = new Ejercicio();
            ejSentadilla.setNombreEjercicio("Sentadilla Trasera Profunda");
            ejSentadilla.setSerie(4);
            ejSentadilla.setRepeticiones("6-8");
            ejSentadilla.setDescansoSegundos("120");
            ejSentadilla.setNotas("Romper el paralelo, pecho alto.");
            ejSentadilla.setOrden(1);
            ejSentadilla.setTipoSerie(TipoSerieEjercicio.DROP_SET);
            diaPierna.addEjercicio(ejSentadilla);

            rutinaBase.addDiasRutina(diaPierna);
            rutinaBase = rutinaRepository.save(rutinaBase);
            log.info("DataInitializer: Creada rutina genérica de prueba con ejercicios.");
        } else {
            rutinaBase = rutinasExistentes.get(0);
        }

        // 4. Alumnos de demostración (incluyendo lucia.fernandez@mail.com y alumno@atlasgym.com)
        Alumno alumnoLucas = asegurarAlumnoDemo(
                "Lucas Pérez", "42123456", "alumno@atlasgym.com", "+54 9 11 4567-8901",
                planMusculacion, rutinaBase, true
        );
        Alumno alumnaLucia = asegurarAlumnoDemo(
                "Lucía Fernández", "39482910", "lucia.fernandez@mail.com", "+54 9 11 5544-3322",
                planFull, rutinaBase, true
        );
        Alumno alumnoMartin = asegurarAlumnoDemo(
                "Martín Gómez", "35882104", "martin.gomez@mail.com", "+54 9 11 6677-8899",
                planMusculacion, rutinaBase, true
        );
        Alumno alumnaSofia = asegurarAlumnoDemo(
                "Sofía Rodríguez", "42109482", "sofia.rodriguez@mail.com", "+54 9 11 2233-4455",
                planEstudiante, null, true
        );

        // 5. Pagos semilla (para que Lucía y Lucas figuren AL_DIA, Martín PENDIENTE y Sofía VENCIDO)
        if (pagoRepository.count() == 0) {
            crearPagoSemilla(alumnaLucia, planFull, new BigDecimal("28000.00"), MetodoPago.TRANSFERENCIA, EstadoPago.PAGADO, periodoActual, LocalDate.now().minusDays(2));
            crearPagoSemilla(alumnoLucas, planMusculacion, new BigDecimal("22000.00"), MetodoPago.EFECTIVO, EstadoPago.PAGADO, periodoActual, LocalDate.now().minusDays(1));
            crearPagoSemilla(alumnoMartin, planMusculacion, new BigDecimal("22000.00"), MetodoPago.TRANSFERENCIA, EstadoPago.PENDIENTE, periodoActual, LocalDate.now().minusDays(3));
            crearPagoSemilla(alumnaSofia, planEstudiante, new BigDecimal("17500.00"), MetodoPago.EFECTIVO, EstadoPago.VENCIDO, periodoActual, LocalDate.now().minusDays(15));
            log.info("DataInitializer: Creados pagos semilla de demostración.");
        }

        // 6. Asistencias semilla del día
        if (asistenciaRepository.count() == 0) {
            Asistencia a1 = new Asistencia(LocalDate.now(), LocalTime.now().minusMinutes(35), alumnaLucia, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA);
            Asistencia a2 = new Asistencia(LocalDate.now(), LocalTime.now().minusMinutes(20), alumnoLucas, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.AL_DIA);
            Asistencia a3 = new Asistencia(LocalDate.now(), LocalTime.now().minusMinutes(50), alumnoMartin, MetodoAsistencia.DNI_TOTEM, EstadoCuenta.PENDIENTE);
            asistenciaRepository.saveAll(List.of(a1, a2, a3));
            log.info("DataInitializer: Creadas asistencias iniciales del día.");
        }

        // 7. Videoteca biomecánica base
        if (videoTecnicaRepository.count() == 0) {
            VideoTecnica v1 = new VideoTecnica();
            v1.setTitulo("Press de Banca Plano con Barra");
            v1.setDescripcion("Ejecución biomecánica óptima para máximo reclutamiento del pectoral mayor protegiendo el manguito rotador.");
            v1.setGrupoMuscularVideo(GrupoMuscular.PECHO);
            v1.setNivel("Intermedio");
            v1.setDuracion("Loop continuo");
            v1.setUrlVideo("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg");
            v1.setThumbnailUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg");
            v1.setGifUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Bench_Press_-_Medium_Grip/0.jpg");
            v1.setFormato(FormatoVideo.GIF);
            v1.setEquipo("Barra olímpica y banco plano");
            v1.setConsejosClave(List.of("Retraer y deprimir escápulas antes de sacar la barra", "Mantener los codos a 45-60 grados del torso", "Empujar con los pies firmes contra el suelo"));

            VideoTecnica v2 = new VideoTecnica();
            v2.setTitulo("Sentadilla Libre con Barra (Back Squat)");
            v2.setDescripcion("Patrón dominante de rodilla y cadera para desarrollo integral del tren inferior y estabilidad del core.");
            v2.setGrupoMuscularVideo(GrupoMuscular.PIERNAS);
            v2.setNivel("Intermedio");
            v2.setDuracion("Loop continuo");
            v2.setUrlVideo("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Full_Squat/0.jpg");
            v2.setThumbnailUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Full_Squat/0.jpg");
            v2.setGifUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Barbell_Full_Squat/0.jpg");
            v2.setFormato(FormatoVideo.GIF);
            v2.setEquipo("Rack de potencia y barra olímpica");
            v2.setConsejosClave(List.of("Tomar aire y generar presión intraabdominal (bracing)", "Alinear rodillas con la punta de los pies", "Mantener el pecho erguido en todo el recorrido"));

            VideoTecnica v3 = new VideoTecnica();
            v3.setTitulo("Dominadas Pronas y Jalón Dorsal");
            v3.setDescripcion("Tracción vertical enfocada en amplitud dorsal y control escapular.");
            v3.setGrupoMuscularVideo(GrupoMuscular.ESPALDA);
            v3.setNivel("Principiante");
            v3.setDuracion("Loop continuo");
            v3.setUrlVideo("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Wide-Grip_Lat_Pulldown/0.jpg");
            v3.setThumbnailUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Wide-Grip_Lat_Pulldown/0.jpg");
            v3.setGifUrl("https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Wide-Grip_Lat_Pulldown/0.jpg");
            v3.setFormato(FormatoVideo.GIF);
            v3.setEquipo("Polea alta o barra de dominadas");
            v3.setConsejosClave(List.of("Iniciar el movimiento deprimiendo los hombros", "Llevar los codos hacia los bolsillos traseros", "Controlar la fase excéntrica durante 2 segundos"));

            videoTecnicaRepository.saveAll(List.of(v1, v2, v3));
            log.info("DataInitializer: Creados videos biomecánicos iniciales.");
        }

        // 8. Aviso institucional de bienvenida
        if (avisoRepository.count() == 0) {
            Aviso avisoBienvenida = new Aviso();
            avisoBienvenida.setTitulo("¡Bienvenidos al nuevo sistema ATLAS Gym!");
            avisoBienvenida.setMensaje("Ya podés consultar tus rutinas desde tu celular, registrar tus pesos serie a serie y hacer check-in en el Tótem de ingreso.");
            avisoBienvenida.setCategoria(CategoriaAviso.IMPORTANTE);
            avisoBienvenida.setFecha(LocalDate.now());
            avisoBienvenida.setParaTodos(true);
            avisoRepository.save(avisoBienvenida);
            log.info("DataInitializer: Creado aviso institucional de bienvenida.");
        }

        log.info("DataInitializer: Verificación de datos semilla completada exitosamente.");
    }

    private Alumno asegurarAlumnoDemo(String nombreCompleto, String dni, String email, String celular, Plan plan, Rutina rutina, boolean activo) {
        var existente = alumnoRepository.findByDni(dni);
        if (existente.isPresent()) {
            Alumno al = existente.get();
            // Asegurar que el nombre completo esté bien formateado
            if (!al.getNombre().equals(nombreCompleto)) {
                al.setNombre(nombreCompleto);
                return alumnoRepository.save(al);
            }
            return al;
        }

        Usuario usuario = usuarioRepository.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("alumno123"));
            u.setRol(Rol.ALUMNO);
            u.setActivo(activo);
            u.setFechaCreacion(LocalDate.now());
            u.setCelular(celular);
            return usuarioRepository.save(u);
        });

        Alumno alumno = new Alumno();
        alumno.setNombre(nombreCompleto);
        alumno.setDni(dni);
        alumno.setEmail(email);
        alumno.setCelular(celular);
        alumno.setFechaAlta(LocalDate.now().minusMonths(1));
        alumno.setUltimaAsistencia(LocalDate.now());
        alumno.setActivo(activo);
        alumno.setUsuario(usuario);
        alumno.setPlanAlumno(plan);
        alumno.setRutinaAlumno(rutina);
        return alumnoRepository.save(alumno);
    }

    private void crearPagoSemilla(Alumno alumno, Plan plan, BigDecimal monto, MetodoPago metodo, EstadoPago estado, String periodoMes, LocalDate fecha) {
        Pago p = new Pago();
        p.setAlumno(alumno);
        p.setPlanPago(plan);
        p.setAlumnoNombreHistorico(alumno.getNombre());
        p.setMonto(monto);
        p.setMetodoPago(metodo);
        p.setEstadoPago(estado);
        p.setPeriodoMes(periodoMes);
        p.setFecha(fecha);
        pagoRepository.save(p);
    }
}
