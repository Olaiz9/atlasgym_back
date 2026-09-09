package com.atlas.backend.Modelo;

import java.time.LocalDate;

public class Asistencia {

    private Long id;
    private LocalDate fecha;
    private Alumno alumno;

    public Asistencia() {
    }

    public Asistencia(Long id, LocalDate fecha, Alumno alumno) {
        this.id = id;
        this.fecha = fecha;
        this.alumno = alumno;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public void setAlumno(Alumno alumno) {
        this.alumno = alumno;
    }
}
