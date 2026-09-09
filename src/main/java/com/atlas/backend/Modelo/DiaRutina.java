package com.atlas.backend.Modelo;

import java.util.ArrayList;
import java.util.List;

public class DiaRutina {

    private Long id;
    private String nombre;
    private int orden;
    private Rutina rutinaDia;
    private List<Ejercicio> ejercicios = new ArrayList<>();
    private List<VideoTecnica> videos = new ArrayList<>();

    public DiaRutina() {
    }

    public DiaRutina(Long id, String nombre, int orden, Rutina rutinaDia, List<Ejercicio> ejercicios, List<VideoTecnica> videos) {
        this.id = id;
        this.nombre = nombre;
        this.orden = orden;
        this.rutinaDia = rutinaDia;
        this.ejercicios = ejercicios;
        this.videos = videos;
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

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public Rutina getRutinaDia() {
        return rutinaDia;
    }

    public void setRutinaDia(Rutina rutinaDia) {
        this.rutinaDia = rutinaDia;
    }

    public List<Ejercicio> getEjercicios() {
        return ejercicios;
    }

    public void setEjercicios(List<Ejercicio> ejercicios) {
        this.ejercicios = ejercicios;
    }

    public void addEjercicio(Ejercicio ejercicio) {
        this.ejercicios.add(ejercicio);
    }

    public void removeEjercicio(Ejercicio ejercicio) {
        this.ejercicios.remove(ejercicio);
    }

    public List<VideoTecnica> getVideos() {
        return videos;
    }

    public void setVideos(List<VideoTecnica> videos) {
        this.videos = videos;
    }

    public void addVideo(VideoTecnica video) {
        this.videos.add(video);
    }

    public void removeVideo(VideoTecnica video) {
        this.videos.remove(video);
    }
}