package com.atlas.backend.Modelo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Alumno {

    private Long idAlumno;
    private String nombre;
    private String apellido;
    private String dni;
    private String celular;
    private LocalDate fechaNacimiento;
    private LocalDate fechaAlta;
    private boolean activo;
    private LocalDate ultimaAsistencia;
    private Usuario usuario;
    private Plan planAlumno;
    private Rutina rutinaAlumno;
    private List<Asistencia> asistencias = new ArrayList<>();
    private List<Pago> pagosAlumnos = new ArrayList<>();

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
        this.asistencias = asistencias;
        this.pagosAlumnos = pagosAlumnos;
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

    public LocalDate getUltimaAsistencia() {
        return ultimaAsistencia;
    }

    public void setUltimaAsistencia(LocalDate ultimaAsistencia) {
        this.ultimaAsistencia = ultimaAsistencia;
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
        this.asistencias.add(asistencia);
    }

    public void removeAsistencias(Asistencia asistencia) {
        this.asistencias.remove(asistencia);
    }

    public List<Pago> getPagosAlumnos() {
        return pagosAlumnos;
    }

    public void setPagosAlumnos(List<Pago> pagosAlumnos) {
        this.pagosAlumnos = pagosAlumnos;
    }

    public void addPagosAlumnos(Pago pago) {
        this.pagosAlumnos.add(pago);
    }

    public void removePagosAlumnos(Pago pago) {
        this.pagosAlumnos.remove(pago);
    }
}
