package com.atlas.backend.Modelo;

public class Ejercicio {

    private Long id;
    private String nombreEjercicio;
    private int serie;
    private String repeticiones;
    private String descansoSegundos;
    private String notas;
    private int orden;
    private DiaRutina diaRutina;
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
