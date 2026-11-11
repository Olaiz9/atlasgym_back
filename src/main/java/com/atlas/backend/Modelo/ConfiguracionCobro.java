package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Fila única (id = 1) con los datos de transferencia que ven los alumnos. */
@Entity
@Table(name = "configuracion_cobro")
public class ConfiguracionCobro {

    public static final long ID_UNICO = 1L;

    @Id
    @Column(name = "id")
    private Long id = ID_UNICO;

    @Column(name = "alias", length = 100)
    private String alias;

    @Column(name = "titular", length = 150)
    private String titular;

    @Column(name = "actualizado_por", length = 120)
    private String actualizadoPor;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public Long getId() { return id; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
