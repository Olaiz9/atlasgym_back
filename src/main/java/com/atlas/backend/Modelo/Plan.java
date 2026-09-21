package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "planes")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Long idPlan;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "dia_por_semana", length = 50)
    private String diaPorSemana;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    public Plan() {
    }

    public Plan(Long idPlan, boolean activo, BigDecimal precio, String diaPorSemana, String descripcion, String nombre) {
        this.idPlan = idPlan;
        this.activo = activo;
        this.precio = precio;
        this.diaPorSemana = diaPorSemana;
        this.descripcion = descripcion;
        this.nombre = nombre;
    }

    public Plan(String nombre, String descripcion, String diaPorSemana, BigDecimal precio, boolean activo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.diaPorSemana = diaPorSemana;
        this.precio = precio;
        this.activo = activo;
    }

    public Long getIdPlan() {
        return idPlan;
    }

    public void setIdPlan(Long idPlan) {
        this.idPlan = idPlan;
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

    public String getDiaPorSemana() {
        return diaPorSemana;
    }

    public void setDiaPorSemana(String diaPorSemana) {
        this.diaPorSemana = diaPorSemana;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
