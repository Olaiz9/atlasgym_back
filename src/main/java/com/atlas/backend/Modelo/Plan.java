package com.atlas.backend.Modelo;

import java.math.BigDecimal;

public class Plan {

    private Long idPlan;
    private String nombre;
    private String descripcion;
    private String diaPorSemana;
    private BigDecimal precio;
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
