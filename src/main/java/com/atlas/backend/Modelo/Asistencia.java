package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "asistencias")
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    public Asistencia() {
    }

    public Asistencia(Long id, LocalDate fecha, Alumno alumno) {
        this.id = id;
        this.fecha = fecha;
        this.alumno = alumno;
    }

    public Asistencia(LocalDate fecha, Alumno alumno) {
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
