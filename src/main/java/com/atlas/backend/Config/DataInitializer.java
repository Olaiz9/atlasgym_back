package com.atlas.backend.Config;

import com.atlas.backend.Modelo.Alumno;
import com.atlas.backend.Modelo.Aviso;
import com.atlas.backend.Modelo.CategoriaAviso;
import com.atlas.backend.Modelo.DiaRutina;
import com.atlas.backend.Modelo.Ejercicio;
import com.atlas.backend.Modelo.ObjetivoRutina;
import com.atlas.backend.Modelo.Plan;
import com.atlas.backend.Modelo.Rol;
import com.atlas.backend.Modelo.Rutina;
import com.atlas.backend.Modelo.TipoSerieEjercicio;
import com.atlas.backend.Modelo.Usuario;
import com.atlas.backend.Repositorio.AlumnoRepository;
import com.atlas.backend.Repositorio.AvisoRepository;
import com.atlas.backend.Repositorio.PlanRepository;
import com.atlas.backend.Repositorio.RutinaRepository;
import com.atlas.backend.Repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final PlanRepository planRepository;
    private final RutinaRepository rutinaRepository;
    private final AvisoRepository avisoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           AlumnoRepository alumnoRepository,
                           PlanRepository planRepository,
                           RutinaRepository rutinaRepository,
                           AvisoRepository avisoRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.alumnoRepository = alumnoRepository;
        this.planRepository = planRepository;
        this.rutinaRepository = rutinaRepository;
        this.avisoRepository = avisoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            log.info("DataInitializer: La base de datos ya contiene usuarios. Omitiendo seed inicial.");
            return;
        }

        log.info("DataInitializer: Base de datos vacía detectada. Inicializando datos originales...");

        // 1. Crear usuario Administrador
        Usuario admin = new Usuario();
        admin.setEmail("admin@atlasgym.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setFechaCreacion(LocalDate.now());
        admin.setCelular("+54 9 11 9876-5432");
        usuarioRepository.save(admin);
        log.info("DataInitializer: Creado usuario ADMIN (admin@atlasgym.com / admin123)");

        // 2. Crear Planes de membresía
        Plan planMusculacion = new Plan();
        planMusculacion.setNombre("Plan Musculación Libre");
        planMusculacion.setPrecio(new BigDecimal("22000.00"));
        planMusculacion.setDiaPorSemana("5 días");
        planMusculacion.setDescripcion("Acceso total e ilimitado a la sala de musculación de lunes a viernes.");
        planMusculacion.setActivo(true);
        planRepository.save(planMusculacion);

        Plan planFull = new Plan();
        planFull.setNombre("Plan Full Pass");
        planFull.setPrecio(new BigDecimal("28000.00"));
        planFull.setDiaPorSemana("6 días");
        planFull.setDescripcion("Musculación + Clases + Seguimiento de entrenamiento.");
        planFull.setActivo(true);
        planRepository.save(planFull);

        Plan planEstudiante = new Plan();
        planEstudiante.setNombre("Plan Estudiante");
        planEstudiante.setPrecio(new BigDecimal("17500.00"));
        planEstudiante.setDiaPorSemana("3 días");
        planEstudiante.setDescripcion("3 días por semana con descuento universitario.");
        planEstudiante.setActivo(true);
        planRepository.save(planEstudiante);
        log.info("DataInitializer: Creados 3 planes de membresía base.");

        // 3. Crear Rutina Genérica con días y ejercicios
        Rutina rutinaBase = new Rutina();
        rutinaBase.setNombre("Rutina Hipertrofia A/B");
        rutinaBase.setDescripcion("Rutina balanceada de fuerza e hipertrofia para desarrollo muscular.");
        rutinaBase.setObjetivo(ObjetivoRutina.HIPERTROFIA);
        rutinaBase.setEsGenerica(true);
        rutinaBase.setFechaCreacion(LocalDate.now());

        DiaRutina diaTorso = new DiaRutina();
        diaTorso.setNombre("Día 1 - Torso & Empuje");
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
        ejRemo.setTipoSerie(TipoSerieEjercicio.NORMAL);
        diaTorso.addEjercicio(ejRemo);

        rutinaBase.addDiasRutina(diaTorso);

        DiaRutina diaPierna = new DiaRutina();
        diaPierna.setNombre("Día 2 - Pierna & Core");
        diaPierna.setOrden(2);

        Ejercicio ejSentadilla = new Ejercicio();
        ejSentadilla.setNombreEjercicio("Sentadilla Trasera Profunda");
        ejSentadilla.setSerie(4);
        ejSentadilla.setRepeticiones("6-8");
        ejSentadilla.setDescansoSegundos("120");
        ejSentadilla.setNotas("Romper el paralelo, pecho alto.");
        ejSentadilla.setOrden(1);
        ejSentadilla.setTipoSerie(TipoSerieEjercicio.NORMAL);
        diaPierna.addEjercicio(ejSentadilla);

        rutinaBase.addDiasRutina(diaPierna);
        rutinaRepository.save(rutinaBase);
        log.info("DataInitializer: Creada rutina genérica de prueba con ejercicios.");

        // 4. Crear usuario y ficha de Alumno original
        Usuario usuarioAlumno = new Usuario();
        usuarioAlumno.setEmail("alumno@atlasgym.com");
        usuarioAlumno.setPassword(passwordEncoder.encode("alumno123"));
        usuarioAlumno.setRol(Rol.ALUMNO);
        usuarioAlumno.setActivo(true);
        usuarioAlumno.setFechaCreacion(LocalDate.now());
        usuarioAlumno.setCelular("+54 9 11 4567-8901");
        usuarioRepository.save(usuarioAlumno);

        Alumno alumnoLucas = new Alumno();
        alumnoLucas.setNombre("Lucas");
        alumnoLucas.setApellido("Pérez");
        alumnoLucas.setDni("42123456");
        alumnoLucas.setEmail("alumno@atlasgym.com");
        alumnoLucas.setCelular("+54 9 11 4567-8901");
        alumnoLucas.setFechaAlta(LocalDate.now().minusMonths(1));
        alumnoLucas.setActivo(true);
        alumnoLucas.setUsuario(usuarioAlumno);
        alumnoLucas.setPlanAlumno(planMusculacion);
        alumnoLucas.setRutinaAlumno(rutinaBase);
        alumnoRepository.save(alumnoLucas);
        log.info("DataInitializer: Creado usuario ALUMNO (alumno@atlasgym.com / alumno123) vinculado a Lucas Pérez (DNI 42123456).");

        // 5. Crear Aviso de Bienvenida
        Aviso avisoBienvenida = new Aviso();
        avisoBienvenida.setTitulo("¡Bienvenidos al nuevo sistema ATLAS Gym!");
        avisoBienvenida.setMensaje("Ya podés consultar tus rutinas desde tu celular, registrar tus pesos serie a serie y hacer check-in en el Tótem de ingreso.");
        avisoBienvenida.setCategoria(CategoriaAviso.IMPORTANTE);
        avisoBienvenida.setFecha(LocalDate.now());
        avisoBienvenida.setParaTodos(true);
        avisoRepository.save(avisoBienvenida);
        log.info("DataInitializer: Creado aviso institucional de bienvenida.");

        log.info("DataInitializer: Inicialización de datos completada exitosamente.");
    }
}
