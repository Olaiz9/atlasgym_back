package com.atlas.backend.Modelo;

import jakarta.persistence.*;

@Entity
@Table(name = "ejercicios")
public class Ejercicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_ejercicio", nullable = false, length = 150)
    private String nombreEjercicio;

    @Column(name = "serie", nullable = false)
    private int serie;

    @Column(name = "repeticiones", length = 50)
    private String repeticiones;

    @Column(name = "descanso_segundos", length = 50)
    private String descansoSegundos;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_serie", length = 30)
    private TipoSerieEjercicio tipoSerie = TipoSerieEjercicio.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dia_rutina_id", nullable = false)
    private DiaRutina diaRutina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_tecnica_id")
    private VideoTecnica videoTecnica;

    public Ejercicio() {
    }

    public Ejercicio(Long id, String nombreEjercicio, int serie, String repeticiones, String descansoSegundos, String notas, int orden, DiaRutina diaRutina, VideoTecnica videoTecnica) {
        this.id = id;
        this.nombreEjercicio = nombreEjercicio;
        this.serie = serie;
        this.repeticiones = repeticiones;
        this.descansoSegundos = descansoSegundos;
        this.notas = notas;
        this.orden = orden;
        this.diaRutina = diaRutina;
        this.videoTecnica = videoTecnica;
        this.tipoSerie = TipoSerieEjercicio.NORMAL;
    }

    public Ejercicio(Long id, String nombreEjercicio, int serie, String repeticiones, String descansoSegundos, String notas, int orden, TipoSerieEjercicio tipoSerie, DiaRutina diaRutina, VideoTecnica videoTecnica) {
        this.id = id;
        this.nombreEjercicio = nombreEjercicio;
        this.serie = serie;
        this.repeticiones = repeticiones;
        this.descansoSegundos = descansoSegundos;
        this.notas = notas;
        this.orden = orden;
        this.tipoSerie = tipoSerie != null ? tipoSerie : TipoSerieEjercicio.NORMAL;
        this.diaRutina = diaRutina;
        this.videoTecnica = videoTecnica;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreEjercicio() {
        return nombreEjercicio;
    }

    public void setNombreEjercicio(String nombreEjercicio) {
        this.nombreEjercicio = nombreEjercicio;
    }

    public int getSerie() {
        return serie;
    }

    public void setSerie(int serie) {
        this.serie = serie;
    }

    public String getRepeticiones() {
        return repeticiones;
    }

    public void setRepeticiones(String repeticiones) {
        this.repeticiones = repeticiones;
    }

    public String getDescansoSegundos() {
        return descansoSegundos;
    }

    public void setDescansoSegundos(String descansoSegundos) {
        this.descansoSegundos = descansoSegundos;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public TipoSerieEjercicio getTipoSerie() {
        return tipoSerie;
    }

    public void setTipoSerie(TipoSerieEjercicio tipoSerie) {
        this.tipoSerie = tipoSerie;
    }

    public DiaRutina getDiaRutina() {
        return diaRutina;
    }

    public void setDiaRutina(DiaRutina diaRutina) {
        this.diaRutina = diaRutina;
    }

    public VideoTecnica getVideoTecnica() {
        return videoTecnica;
    }

    public void setVideoTecnica(VideoTecnica videoTecnica) {
        this.videoTecnica = videoTecnica;
    }
}
