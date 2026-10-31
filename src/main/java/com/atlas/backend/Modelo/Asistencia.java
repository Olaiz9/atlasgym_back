package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "asistencias", indexes = {
    @Index(name = "idx_asistencias_alumno_fecha", columnList = "alumno_id, fecha"),
    @Index(name = "idx_asistencias_fecha_hora", columnList = "fecha, hora")
})
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora")
    private LocalTime hora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", length = 30)
    private MetodoAsistencia metodo = MetodoAsistencia.DNI_TOTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", length = 30)
    private EstadoCuenta estadoCuenta;

    @Column(name = "client_event_id", length = 64, unique = true)
    private String clientEventId;

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

    public Asistencia(Long id, LocalDate fecha, LocalTime hora, Alumno alumno, MetodoAsistencia metodo, EstadoCuenta estadoCuenta) {
        this.id = id;
        this.fecha = fecha;
        this.hora = hora;
        this.alumno = alumno;
        this.metodo = metodo;
        this.estadoCuenta = estadoCuenta;
    }

    public Asistencia(LocalDate fecha, LocalTime hora, Alumno alumno, MetodoAsistencia metodo, EstadoCuenta estadoCuenta) {
        this.fecha = fecha;
        this.hora = hora;
        this.alumno = alumno;
        this.metodo = metodo;
        this.estadoCuenta = estadoCuenta;
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

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public void setAlumno(Alumno alumno) {
        this.alumno = alumno;
    }

    public MetodoAsistencia getMetodo() {
        return metodo;
    }

    public void setMetodo(MetodoAsistencia metodo) {
        this.metodo = metodo;
    }

    public EstadoCuenta getEstadoCuenta() {
        return estadoCuenta;
    }

    public void setEstadoCuenta(EstadoCuenta estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }

    public String getClientEventId() {
        return clientEventId;
    }

    public void setClientEventId(String clientEventId) {
        this.clientEventId = clientEventId;
    }
}
