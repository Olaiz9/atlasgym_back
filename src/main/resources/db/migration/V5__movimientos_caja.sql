-- ============================================================================
-- ATLAS Gym - Flyway V5: Movimientos de caja
-- Cada ingreso de dinero (pago total, seña, saldo, ajuste) queda registrado con su propio
-- día, medio de pago y titular. Antes, completar una seña pisaba método/operador/fecha del
-- comprobante y el arqueo diario atribuía todo al último cobro.
-- ============================================================================

CREATE TABLE movimientos_caja (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pago_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    monto DECIMAL(10, 2) NOT NULL,
    metodo_pago VARCHAR(30) NOT NULL,
    fecha DATE NOT NULL,
    fecha_hora DATETIME(6),
    registrado_por VARCHAR(120),
    CONSTRAINT fk_movcaja_pago FOREIGN KEY (pago_id) REFERENCES pagos (id_pago)
);

CREATE INDEX idx_movcaja_fecha ON movimientos_caja (fecha);
CREATE INDEX idx_movcaja_pago ON movimientos_caja (pago_id);

-- Backfill: un movimiento por cada comprobante existente que ingresó dinero, con los datos
-- que tenía el comprobante (no se puede reconstruir el desglose de señas ya completadas).
INSERT INTO movimientos_caja (pago_id, tipo, monto, metodo_pago, fecha, fecha_hora, registrado_por)
SELECT id_pago,
       CASE WHEN estado_pago = 'PARCIAL' THEN 'SENIA' ELSE 'PAGO_TOTAL' END,
       CASE
           WHEN monto_abonado IS NOT NULL AND monto_abonado > 0 THEN monto_abonado
           ELSE monto
       END,
       metodo_pago,
       fecha,
       NULL,
       registrado_por
FROM pagos
WHERE (monto_abonado IS NOT NULL AND monto_abonado > 0)
   OR (monto_abonado IS NULL AND estado_pago IN ('PAGADO', 'ANULADO'));
