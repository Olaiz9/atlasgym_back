package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sesiones_entrenamiento")
public class SesionEntrenamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rutina_id")
    private Rutina rutina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dia_rutina_id")
    private DiaRutina diaRutina;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "duracion_minutos")
    private Integer duracionMinutos;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @OneToMany(mappedBy = "sesionEntrenamiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SesionEjercicio> ejercicios = new ArrayList<>();

    public SesionEntrenamiento() {
    }

    public SesionEntrenamiento(Long id, Alumno alumno, Rutina rutina, DiaRutina diaRutina, LocalDate fecha, Integer duracionMinutos, String notas, List<SesionEjercicio> ejercicios) {
        this.id = id;
        this.alumno = alumno;
        this.rutina = rutina;
        this.diaRutina = diaRutina;
        this.fecha = fecha;
        this.duracionMinutos = duracionMinutos;
        this.notas = notas;
        this.ejercicios = ejercicios != null ? ejercicios : new ArrayList<>();
    }

    public SesionEntrenamiento(Alumno alumno, Rutina rutina, DiaRutina diaRutina, LocalDate fecha, Integer duracionMinutos, String notas) {
        this.alumno = alumno;
        this.rutina = rutina;
        this.diaRutina = diaRutina;
        this.fecha = fecha;
        this.duracionMinutos = duracionMinutos;
        this.notas = notas;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public void setAlumno(Alumno alumno) {
        this.alumno = alumno;
    }

    public Rutina getRutina() {
        return rutina;
    }

    public void setRutina(Rutina rutina) {
        this.rutina = rutina;
    }

    public DiaRutina getDiaRutina() {
        return diaRutina;
    }

    public void setDiaRutina(DiaRutina diaRutina) {
        this.diaRutina = diaRutina;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Integer getDuracionMinutos() {
        return duracionMinutos;
    }

    public void setDuracionMinutos(Integer duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public List<SesionEjercicio> getEjercicios() {
        return ejercicios;
    }

    public void setEjercicios(List<SesionEjercicio> ejercicios) {
        this.ejercicios = ejercicios;
    }

    public void addEjercicio(SesionEjercicio sesionEjercicio) {
        if (sesionEjercicio != null) {
            this.ejercicios.add(sesionEjercicio);
            sesionEjercicio.setSesionEntrenamiento(this);
        }
    }

    public void removeEjercicio(SesionEjercicio sesionEjercicio) {
        if (sesionEjercicio != null) {
            this.ejercicios.remove(sesionEjercicio);
            sesionEjercicio.setSesionEntrenamiento(null);
        }
    }
}
