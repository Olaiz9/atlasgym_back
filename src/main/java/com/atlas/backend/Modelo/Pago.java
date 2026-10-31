package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pagos",
        indexes = {
                @Index(name = "idx_pagos_alumno_fecha", columnList = "alumno_id, fecha"),
                @Index(name = "idx_pagos_fecha_estado", columnList = "fecha, estado_pago")
        }
)
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "monto_abonado", precision = 10, scale = 2)
    private BigDecimal montoAbonado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "mes_correspondiente", length = 50)
    private String mesCorrespondiente;

    @Column(name = "periodo_mes", length = 7)
    private String periodoMes;

    @Column(name = "cantidad_meses")
    private Integer cantidadMeses = 1;

    @Column(name = "alumno_nombre_historico", length = 150)
    private String alumnoNombreHistorico;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 30)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 30)
    private EstadoPago estadoPago;

    @Column(name = "comprobante", length = 255)
    private String comprobante;

    @Column(name = "registrado_por", length = 120)
    private String registradoPor;

    @Column(name = "anulado_por", length = 120)
    private String anuladoPor;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan planPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id")
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

    public Pago(Long idPago, BigDecimal monto, LocalDate fecha, String mesCorrespondiente, String periodoMes, String alumnoNombreHistorico, MetodoPago metodoPago, EstadoPago estadoPago, String comprobante, Plan planPago, Alumno alumno) {
        this.idPago = idPago;
        this.monto = monto;
        this.fecha = fecha;
        this.mesCorrespondiente = mesCorrespondiente;
        this.periodoMes = periodoMes;
        this.alumnoNombreHistorico = alumnoNombreHistorico;
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

    public BigDecimal getMontoAbonado() {
        return montoAbonado;
    }

    public void setMontoAbonado(BigDecimal montoAbonado) {
        this.montoAbonado = montoAbonado;
    }

    /**
     * Devuelve el importe efectivamente ingresado a caja por este comprobante:
     * - PAGADO: montoAbonado (si existe) o monto total.
     * - PARCIAL: montoAbonado (seña entregada).
     * - PENDIENTE / VENCIDO / ANULADO: 0.
     */
    public BigDecimal getMontoRecaudadoEfectivo() {
        if (estadoPago == EstadoPago.PAGADO) {
            return montoAbonado != null ? montoAbonado : (monto != null ? monto : BigDecimal.ZERO);
        }
        if (estadoPago == EstadoPago.PARCIAL) {
            return montoAbonado != null ? montoAbonado : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
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

    public String getPeriodoMes() {
        return periodoMes;
    }

    public void setPeriodoMes(String periodoMes) {
        this.periodoMes = periodoMes;
    }

    public Integer getCantidadMeses() {
        return (cantidadMeses != null && cantidadMeses > 0) ? cantidadMeses : 1;
    }

    public void setCantidadMeses(Integer cantidadMeses) {
        this.cantidadMeses = (cantidadMeses != null && cantidadMeses > 0) ? cantidadMeses : 1;
    }

    public String getAlumnoNombreHistorico() {
        return alumnoNombreHistorico;
    }

    public void setAlumnoNombreHistorico(String alumnoNombreHistorico) {
        this.alumnoNombreHistorico = alumnoNombreHistorico;
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

    public String getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(String registradoPor) {
        this.registradoPor = registradoPor;
    }

    public String getAnuladoPor() {
        return anuladoPor;
    }

    public void setAnuladoPor(String anuladoPor) {
        this.anuladoPor = anuladoPor;
    }

    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public void setMotivoAnulacion(String motivoAnulacion) {
        this.motivoAnulacion = motivoAnulacion;
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
