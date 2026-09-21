package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "videos_tecnica")
public class VideoTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "grupo_muscular", length = 50)
    private GrupoMuscular grupoMuscularVideo;

    @Column(name = "nivel", length = 50)
    private String nivel;

    @Column(name = "duracion", length = 50)
    private String duracion;

    @Column(name = "url_video", length = 500)
    private String urlVideo;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "gif_url", length = 500)
    private String gifUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato", length = 20)
    private FormatoVideo formato;

    @Column(name = "equipo", length = 100)
    private String equipo;

    @ElementCollection
    @CollectionTable(name = "video_consejos", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "consejo", length = 255)
    private List<String> consejosClave = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
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

    public VideoTecnica(Long id, String titulo, String descripcion, GrupoMuscular grupoMuscularVideo, String nivel, String duracion, String urlVideo, String thumbnailUrl, String gifUrl, FormatoVideo formato, String equipo, List<String> consejosClave, Usuario usuario) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.grupoMuscularVideo = grupoMuscularVideo;
        this.nivel = nivel;
        this.duracion = duracion;
        this.urlVideo = urlVideo;
        this.thumbnailUrl = thumbnailUrl;
        this.gifUrl = gifUrl;
        this.formato = formato;
        this.equipo = equipo;
        this.consejosClave = consejosClave != null ? consejosClave : new ArrayList<>();
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

    public String getGifUrl() {
        return gifUrl;
    }

    public void setGifUrl(String gifUrl) {
        this.gifUrl = gifUrl;
    }

    public FormatoVideo getFormato() {
        return formato;
    }

    public void setFormato(FormatoVideo formato) {
        this.formato = formato;
    }

    public String getEquipo() {
        return equipo;
    }

    public void setEquipo(String equipo) {
        this.equipo = equipo;
    }

    public List<String> getConsejosClave() {
        return consejosClave;
    }

    public void setConsejosClave(List<String> consejosClave) {
        this.consejosClave = consejosClave;
    }

    public void addConsejoClave(String consejo) {
        if (consejo != null) {
            this.consejosClave.add(consejo);
        }
    }

    public void removeConsejoClave(String consejo) {
        if (consejo != null) {
            this.consejosClave.remove(consejo);
        }
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}
