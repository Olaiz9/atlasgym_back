package com.atlas.backend.Modelo;

/** Qué representa un ingreso (o ajuste) de caja asociado a un comprobante de cuota. */
public enum TipoMovimientoCaja {
    /** Cuota cobrada completa en un solo pago. */
    PAGO_TOTAL,
    /** Seña / entrega parcial al registrar el comprobante. */
    SENIA,
    /** Saldo restante de una seña ("Completar saldo"). */
    SALDO,
    /** Corrección por cambio manual de estado (puede ser negativa). */
    AJUSTE
}
