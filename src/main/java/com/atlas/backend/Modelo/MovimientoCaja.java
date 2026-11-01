package com.atlas.backend.Modelo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cada ingreso de dinero a caja, con SU día, SU medio de pago y SU titular.
 * Un comprobante (Pago) puede tener varios: p. ej. seña en efectivo cobrada por un hermano
 * el lunes + saldo por transferencia cobrado por el otro el jueves. El arqueo diario se arma
 * sumando movimientos, nunca a partir del estado final del comprobante.
 */
@Entity
@Table(
        name = "movimientos_caja",
        indexes = {
                @Index(name = "idx_movcaja_fecha", columnList = "fecha"),
                @Index(name = "idx_movcaja_pago", columnList = "pago_id")
        }
)
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimientoCaja tipo;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 30)
    private MetodoPago metodoPago;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "registrado_por", length = 120)
    private String registradoPor;

    public MovimientoCaja() {
    }

    public MovimientoCaja(Pago pago, TipoMovimientoCaja tipo, BigDecimal monto, MetodoPago metodoPago,
                          LocalDate fecha, String registradoPor) {
        this.pago = pago;
        this.tipo = tipo;
        this.monto = monto;
        this.metodoPago = metodoPago;
        this.fecha = fecha;
        this.fechaHora = LocalDateTime.now();
        this.registradoPor = registradoPor;
    }

    public Long getId() { return id; }
    public Pago getPago() { return pago; }
    public void setPago(Pago pago) { this.pago = pago; }
    public TipoMovimientoCaja getTipo() { return tipo; }
    public void setTipo(TipoMovimientoCaja tipo) { this.tipo = tipo; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
}
