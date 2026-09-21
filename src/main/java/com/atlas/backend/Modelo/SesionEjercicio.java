package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sesiones_ejercicios")
public class SesionEjercicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_id", nullable = false)
    private SesionEntrenamiento sesionEntrenamiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ejercicio_id")
    private Ejercicio ejercicio;

    @Column(name = "nombre_ejercicio_historico", length = 150)
    private String nombreEjercicioHistorico;

    @OneToMany(mappedBy = "sesionEjercicio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegistroSerie> series = new ArrayList<>();

    public SesionEjercicio() {
    }

    public SesionEjercicio(Long id, SesionEntrenamiento sesionEntrenamiento, Ejercicio ejercicio, String nombreEjercicioHistorico, List<RegistroSerie> series) {
        this.id = id;
        this.sesionEntrenamiento = sesionEntrenamiento;
        this.ejercicio = ejercicio;
        this.nombreEjercicioHistorico = nombreEjercicioHistorico;
        this.series = series != null ? series : new ArrayList<>();
    }

    public SesionEjercicio(Ejercicio ejercicio, String nombreEjercicioHistorico) {
        this.ejercicio = ejercicio;
        this.nombreEjercicioHistorico = nombreEjercicioHistorico;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SesionEntrenamiento getSesionEntrenamiento() {
        return sesionEntrenamiento;
    }

    public void setSesionEntrenamiento(SesionEntrenamiento sesionEntrenamiento) {
        this.sesionEntrenamiento = sesionEntrenamiento;
    }

    public Ejercicio getEjercicio() {
        return ejercicio;
    }

    public void setEjercicio(Ejercicio ejercicio) {
        this.ejercicio = ejercicio;
    }

    public String getNombreEjercicioHistorico() {
        return nombreEjercicioHistorico;
    }

    public void setNombreEjercicioHistorico(String nombreEjercicioHistorico) {
        this.nombreEjercicioHistorico = nombreEjercicioHistorico;
    }

    public List<RegistroSerie> getSeries() {
        return series;
    }

    public void setSeries(List<RegistroSerie> series) {
        this.series = series;
    }

    public void addSerie(RegistroSerie serie) {
        if (serie != null) {
            this.series.add(serie);
            serie.setSesionEjercicio(this);
        }
    }

    public void removeSerie(RegistroSerie serie) {
        if (serie != null) {
            this.series.remove(serie);
            serie.setSesionEjercicio(null);
        }
    }
}
