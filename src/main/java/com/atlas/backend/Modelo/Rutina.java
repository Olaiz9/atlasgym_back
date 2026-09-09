package com.atlas.backend.Modelo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List; // <-- Agregado

public class Rutina {

    private Long id; // <-- Long con mayúscula
    private String nombre;
    private String descripcion;
    private ObjetivoRutina objetivo;
    private boolean esGenerica;
    private LocalDate fechaCreacion;
    private List<DiaRutina> diasRutina = new ArrayList<>(); //List en vez de ArrayList

    public Rutina() {
    }

    public Rutina(Long id, String descripcion, String nombre, ObjetivoRutina objetivo, boolean esGenerica, LocalDate fechaCreacion, List<DiaRutina> diasRutina) {
        this.id = id;
        this.descripcion = descripcion;
        this.nombre = nombre;
        this.objetivo = objetivo;
        this.esGenerica = esGenerica;
        this.fechaCreacion = fechaCreacion;
        this.diasRutina = diasRutina;
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
        this.diasRutina.add(diaRutina);
    }
    public void removeDiasRutina(DiaRutina diaRutina) {
        this.diasRutina.remove(diaRutina);
    }


}