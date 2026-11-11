package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rutinas")
public class Rutina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "objetivo", length = 50)
    private ObjetivoRutina objetivo;

    @Column(name = "es_generica", nullable = false)
    private boolean esGenerica;

    @Column(name = "fecha_creacion")
    private LocalDate fechaCreacion;

    @OneToMany(mappedBy = "rutinaDia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiaRutina> diasRutina = new ArrayList<>();

    public Rutina() {
    }

    public Rutina(Long id, String descripcion, String nombre, ObjetivoRutina objetivo, boolean esGenerica, LocalDate fechaCreacion, List<DiaRutina> diasRutina) {
        this.id = id;
        this.descripcion = descripcion;
        this.nombre = nombre;
        this.objetivo = objetivo;
        this.esGenerica = esGenerica;
        this.fechaCreacion = fechaCreacion;
        this.diasRutina = diasRutina != null ? diasRutina : new ArrayList<>();
    }

    public Rutina(String nombre, String descripcion, ObjetivoRutina objetivo, boolean esGenerica, LocalDate fechaCreacion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.objetivo = objetivo;
        this.esGenerica = esGenerica;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public ObjetivoRutina getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(ObjetivoRutina objetivo) {
        this.objetivo = objetivo;
    }

    public boolean isEsGenerica() {
        return esGenerica;
    }

    public void setEsGenerica(boolean esGenerica) {
        this.esGenerica = esGenerica;
    }

    public LocalDate getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDate fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<DiaRutina> getDiasRutina() {
        return diasRutina;
    }

    public void setDiasRutina(List<DiaRutina> diasRutina) {
        this.diasRutina = diasRutina;
    }

    public void addDiasRutina(DiaRutina diaRutina) {
        if (diaRutina != null) {
            this.diasRutina.add(diaRutina);
            diaRutina.setRutinaDia(this);
        }
    }

    public void removeDiasRutina(DiaRutina diaRutina) {
        if (diaRutina != null) {
            this.diasRutina.remove(diaRutina);
            diaRutina.setRutinaDia(null);
        }
    }
}