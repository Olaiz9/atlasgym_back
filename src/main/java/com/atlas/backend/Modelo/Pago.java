package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "mes_correspondiente", length = 50)
    private String mesCorrespondiente;

    @Column(name = "periodo_mes", length = 7)
    private String periodoMes;

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
