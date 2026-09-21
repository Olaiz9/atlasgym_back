package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "avisos")
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    private CategoriaAviso categoria;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "para_todos", nullable = false)
    private boolean paraTodos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;

    @ElementCollection
    @CollectionTable(name = "aviso_leidos", joinColumns = @JoinColumn(name = "aviso_id"))
    @Column(name = "usuario_id")
    private Set<Long> leidoPor = new HashSet<>();

    public Aviso() {
    }

    public Aviso(Long id, String titulo, String mensaje, CategoriaAviso categoria, LocalDate fecha, boolean paraTodos, Alumno alumno, Set<Long> leidoPor) {
        this.id = id;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.categoria = categoria;
        this.fecha = fecha;
        this.paraTodos = paraTodos;
        this.alumno = alumno;
        this.leidoPor = leidoPor != null ? leidoPor : new HashSet<>();
    }

    public Aviso(String titulo, String mensaje, CategoriaAviso categoria, LocalDate fecha, boolean paraTodos) {
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.categoria = categoria;
        this.fecha = fecha;
        this.paraTodos = paraTodos;
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

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public CategoriaAviso getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaAviso categoria) {
        this.categoria = categoria;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public boolean isParaTodos() {
        return paraTodos;
    }

    public void setParaTodos(boolean paraTodos) {
        this.paraTodos = paraTodos;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public void setAlumno(Alumno alumno) {
        this.alumno = alumno;
    }

    public Set<Long> getLeidoPor() {
        return leidoPor;
    }

    public void setLeidoPor(Set<Long> leidoPor) {
        this.leidoPor = leidoPor;
    }

    public void marcarLeido(Long usuarioId) {
        if (usuarioId != null) {
            this.leidoPor.add(usuarioId);
        }
    }

    public boolean haSidoLeidoPor(Long usuarioId) {
        return usuarioId != null && this.leidoPor.contains(usuarioId);
    }
}
