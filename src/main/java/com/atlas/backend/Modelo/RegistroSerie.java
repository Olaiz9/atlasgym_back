package com.atlas.backend.Modelo;

import jakarta.persistence.*;

@Entity
@Table(name = "registros_series")
public class RegistroSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_ejercicio_id", nullable = false)
    private SesionEjercicio sesionEjercicio;

    @Column(name = "serie_numero", nullable = false)
    private int serieNumero;

    @Column(name = "kg")
    private Double kg;

    @Column(name = "reps", nullable = false)
    private int reps;

    @Column(name = "completada", nullable = false)
    private boolean completada;

    public RegistroSerie() {
    }

    public RegistroSerie(Long id, SesionEjercicio sesionEjercicio, int serieNumero, Double kg, int reps, boolean completada) {
        this.id = id;
        this.sesionEjercicio = sesionEjercicio;
        this.serieNumero = serieNumero;
        this.kg = kg;
        this.reps = reps;
        this.completada = completada;
    }

    public RegistroSerie(int serieNumero, Double kg, int reps, boolean completada) {
        this.serieNumero = serieNumero;
        this.kg = kg;
        this.reps = reps;
        this.completada = completada;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SesionEjercicio getSesionEjercicio() {
        return sesionEjercicio;
    }

    public void setSesionEjercicio(SesionEjercicio sesionEjercicio) {
        this.sesionEjercicio = sesionEjercicio;
    }

    public int getSerieNumero() {
        return serieNumero;
    }

    public void setSerieNumero(int serieNumero) {
        this.serieNumero = serieNumero;
    }

    public Double getKg() {
        return kg;
    }

    public void setKg(Double kg) {
        this.kg = kg;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public boolean isCompletada() {
        return completada;
    }

    public void setCompletada(boolean completada) {
        this.completada = completada;
    }
}
