package com.atlas.backend.Modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Pago {

    private Long idPago;
    private BigDecimal monto;
    private LocalDate fecha;
    private String mesCorrespondiente;
    private MetodoPago metodoPago;
    private EstadoPago estadoPago;
    private String comprobante;
    private Plan planPago;
    private Alumno alumno;

    public Pago() {
    }

    public Pago(Long idPago, BigDecimal monto, LocalDate fecha, String mesCorrespondiente, MetodoPago metodoPago, EstadoPago estadoPago, String comprobante, Plan planPago, Alumno alumno) {
        this.idPago = idPago;
        this.monto = monto;
        this.fecha = fecha;
        this.mesCorrespondiente = mesCorrespondiente;
        this.metodoPago = metodoPago;
        this.estadoPago = estadoPago;
        this.comprobante = comprobante;
        this.planPago = planPago;
        this.alumno = alumno;
    }

    public Long getIdPago() {
        return idPago;
    }

    public void setIdPago(Long idPago) {
        this.idPago = idPago;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getMesCorrespondiente() {
        return mesCorrespondiente;
    }

    public void setMesCorrespondiente(String mesCorrespondiente) {
        this.mesCorrespondiente = mesCorrespondiente;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public EstadoPago getEstadoPago() {
        return estadoPago;
    }

    public void setEstadoPago(EstadoPago estadoPago) {
        this.estadoPago = estadoPago;
    }

    public String getComprobante() {
        return comprobante;
    }

    public void setComprobante(String comprobante) {
        this.comprobante = comprobante;
    }

    public Plan getPlanPago() {
        return planPago;
    }

    public void setPlanPago(Plan planPago) {
        this.planPago = planPago;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public void setAlumno(Alumno alumno) {
        this.alumno = alumno;
    }
}
