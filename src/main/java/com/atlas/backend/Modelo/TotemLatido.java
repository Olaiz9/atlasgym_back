package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Fila única (id = 1) con la última señal de vida de la tablet del Tótem. */
@Entity
@Table(name = "totem_latido")
public class TotemLatido {

    public static final long ID_UNICO = 1L;

    @Id
    @Column(name = "id")
    private Long id = ID_UNICO;

    @Column(name = "ultimo_latido")
    private LocalDateTime ultimoLatido;

    /** Ingresos que la tablet tenía guardados sin subir en su última señal. */
    @Column(name = "pendientes_offline", nullable = false)
    private int pendientesOffline;

    public Long getId() { return id; }
    public LocalDateTime getUltimoLatido() { return ultimoLatido; }
    public void setUltimoLatido(LocalDateTime ultimoLatido) { this.ultimoLatido = ultimoLatido; }
    public int getPendientesOffline() { return pendientesOffline; }
    public void setPendientesOffline(int pendientesOffline) { this.pendientesOffline = pendientesOffline; }
}
