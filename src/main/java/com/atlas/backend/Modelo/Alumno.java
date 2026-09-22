package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alumnos")
public class Alumno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alumno")
    private Long idAlumno;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "dni", length = 20, unique = true)
    private String dni;

    @Column(name = "celular", length = 30)
    private String celular;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "ultima_asistencia")
    private LocalDate ultimaAsistencia;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "usuario_id", referencedColumnName = "id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan planAlumno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rutina_id")
    private Rutina rutinaAlumno;

    @OneToMany(mappedBy = "alumno", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Asistencia> asistencias = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Pago> pagosAlumnos = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<SesionEntrenamiento> sesionesEntrenamiento = new ArrayList<>();

    public Alumno() {
    }

    public Alumno(Long idAlumno, String nombre, String apellido, String dni, String celular, LocalDate fechaNacimiento, LocalDate fechaAlta, boolean activo, LocalDate ultimaAsistencia, Usuario usuario, Plan planAlumno, Rutina rutinaAlumno, List<Asistencia> asistencias, List<Pago> pagosAlumnos) {
        this.idAlumno = idAlumno;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.celular = celular;
        this.fechaNacimiento = fechaNacimiento;
        this.fechaAlta = fechaAlta;
        this.activo = activo;
        this.ultimaAsistencia = ultimaAsistencia;
        this.usuario = usuario;
        this.planAlumno = planAlumno;
        this.rutinaAlumno = rutinaAlumno;
        this.asistencias = asistencias != null ? asistencias : new ArrayList<>();
        this.pagosAlumnos = pagosAlumnos != null ? pagosAlumnos : new ArrayList<>();
    }

    public Alumno(Long idAlumno, String nombre, String apellido, String dni, String celular, LocalDate fechaNacimiento, LocalDate fechaAlta, boolean activo, LocalDate ultimaAsistencia, String fotoUrl, Usuario usuario, Plan planAlumno, Rutina rutinaAlumno, List<Asistencia> asistencias, List<Pago> pagosAlumnos, List<SesionEntrenamiento> sesionesEntrenamiento) {
        this.idAlumno = idAlumno;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.celular = celular;
        this.fechaNacimiento = fechaNacimiento;
        this.fechaAlta = fechaAlta;
        this.activo = activo;
        this.ultimaAsistencia = ultimaAsistencia;
        this.fotoUrl = fotoUrl;
        this.usuario = usuario;
        this.planAlumno = planAlumno;
        this.rutinaAlumno = rutinaAlumno;
        this.asistencias = asistencias != null ? asistencias : new ArrayList<>();
        this.pagosAlumnos = pagosAlumnos != null ? pagosAlumnos : new ArrayList<>();
        this.sesionesEntrenamiento = sesionesEntrenamiento != null ? sesionesEntrenamiento : new ArrayList<>();
    }

    public Long getIdAlumno() {
        return idAlumno;
    }

    public void setIdAlumno(Long idAlumno) {
        this.idAlumno = idAlumno;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public LocalDate getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(LocalDate fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Baja administrativa: conserva la ficha y todas sus relaciones historicas. */
    public void darDeBaja() {
        this.activo = false;
    }

    /** Evita que un borrado JPA accidental sustituya la baja administrativa. */
    @PreRemove
    private void impedirBorradoFisico() {
        throw new IllegalStateException(
                "El alumno conserva su historial. Use darDeBaja() en lugar de eliminarlo.");
    }

    public LocalDate getUltimaAsistencia() {
        return ultimaAsistencia;
    }

    public void setUltimaAsistencia(LocalDate ultimaAsistencia) {
        this.ultimaAsistencia = ultimaAsistencia;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Plan getPlanAlumno() {
        return planAlumno;
    }

    public void setPlanAlumno(Plan planAlumno) {
        this.planAlumno = planAlumno;
    }

    public Rutina getRutinaAlumno() {
        return rutinaAlumno;
    }

    public void setRutinaAlumno(Rutina rutinaAlumno) {
        this.rutinaAlumno = rutinaAlumno;
    }

    public List<Asistencia> getAsistencias() {
        return asistencias;
    }

    public void setAsistencias(List<Asistencia> asistencias) {
        this.asistencias = asistencias;
    }

    public void addAsistencias(Asistencia asistencia) {
        if (asistencia != null) {
            this.asistencias.add(asistencia);
            asistencia.setAlumno(this);
        }
    }

    public void removeAsistencias(Asistencia asistencia) {
        if (asistencia != null) {
            this.asistencias.remove(asistencia);
            asistencia.setAlumno(null);
        }
    }

    public List<Pago> getPagosAlumnos() {
        return pagosAlumnos;
    }

    public void setPagosAlumnos(List<Pago> pagosAlumnos) {
        this.pagosAlumnos = pagosAlumnos;
    }

    public void addPagosAlumnos(Pago pago) {
        if (pago != null) {
            this.pagosAlumnos.add(pago);
            pago.setAlumno(this);
        }
    }

    public void removePagosAlumnos(Pago pago) {
        if (pago != null) {
            this.pagosAlumnos.remove(pago);
            pago.setAlumno(null);
        }
    }

    public List<SesionEntrenamiento> getSesionesEntrenamiento() {
        return sesionesEntrenamiento;
    }

    public void setSesionesEntrenamiento(List<SesionEntrenamiento> sesionesEntrenamiento) {
        this.sesionesEntrenamiento = sesionesEntrenamiento;
    }

    public void addSesionEntrenamiento(SesionEntrenamiento sesion) {
        if (sesion != null) {
            this.sesionesEntrenamiento.add(sesion);
            sesion.setAlumno(this);
        }
    }

    public void removeSesionEntrenamiento(SesionEntrenamiento sesion) {
        if (sesion != null) {
            this.sesionesEntrenamiento.remove(sesion);
            sesion.setAlumno(null);
        }
    }
}
