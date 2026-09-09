package com.atlas.backend.Modelo;

public class VideoTecnica {

    private Long id;
    private String titulo;
    private String descripcion;
    private GrupoMuscular grupoMuscularVideo;
    private String nivel;
    private String duracion;
    private String urlVideo;
    private String thumbnailUrl;
    private Usuario usuario;

    public VideoTecnica() {
    }

    public VideoTecnica(Long id, String titulo, String descripcion, GrupoMuscular grupoMuscularVideo, String nivel, String duracion, String urlVideo, String thumbnailUrl, Usuario usuario) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.grupoMuscularVideo = grupoMuscularVideo;
        this.nivel = nivel;
        this.duracion = duracion;
        this.urlVideo = urlVideo;
        this.thumbnailUrl = thumbnailUrl;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public GrupoMuscular getGrupoMuscularVideo() {
        return grupoMuscularVideo;
    }

    public void setGrupoMuscularVideo(GrupoMuscular grupoMuscularVideo) {
        this.grupoMuscularVideo = grupoMuscularVideo;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getDuracion() {
        return duracion;
    }

    public void setDuracion(String duracion) {
        this.duracion = duracion;
    }

    public String getUrlVideo() {
        return urlVideo;
    }

    public void setUrlVideo(String urlVideo) {
        this.urlVideo = urlVideo;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}
