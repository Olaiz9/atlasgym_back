package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dias_rutina")
public class DiaRutina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "orden", nullable = false)
    private int orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rutina_id", nullable = false)
    private Rutina rutinaDia;

    @OneToMany(mappedBy = "diaRutina", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ejercicio> ejercicios = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "dias_rutina_videos",
        joinColumns = @JoinColumn(name = "dia_rutina_id"),
        inverseJoinColumns = @JoinColumn(name = "video_tecnica_id")
    )
    private List<VideoTecnica> videos = new ArrayList<>();

    public DiaRutina() {
    }

    public DiaRutina(Long id, String nombre, int orden, Rutina rutinaDia, List<Ejercicio> ejercicios, List<VideoTecnica> videos) {
        this.id = id;
        this.nombre = nombre;
        this.orden = orden;
        this.rutinaDia = rutinaDia;
        this.ejercicios = ejercicios != null ? ejercicios : new ArrayList<>();
        this.videos = videos != null ? videos : new ArrayList<>();
    }

    public DiaRutina(String nombre, int orden, Rutina rutinaDia) {
        this.nombre = nombre;
        this.orden = orden;
        this.rutinaDia = rutinaDia;
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
        if (ejercicio != null) {
            this.ejercicios.add(ejercicio);
            ejercicio.setDiaRutina(this);
        }
    }

    public void removeEjercicio(Ejercicio ejercicio) {
        if (ejercicio != null) {
            this.ejercicios.remove(ejercicio);
            ejercicio.setDiaRutina(null);
        }
    }

    public List<VideoTecnica> getVideos() {
        return videos;
    }

    public void setVideos(List<VideoTecnica> videos) {
        this.videos = videos;
    }

    public void addVideo(VideoTecnica video) {
        if (video != null) {
            this.videos.add(video);
        }
    }

    public void removeVideo(VideoTecnica video) {
        if (video != null) {
            this.videos.remove(video);
        }
    }
}