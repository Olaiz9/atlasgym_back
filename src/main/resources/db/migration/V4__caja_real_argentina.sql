-- ============================================================================
-- ATLAS Gym - Flyway V4: Caja Real Argentina (Señas Parciales, Anulación Auditada y Arqueo)
-- ============================================================================

ALTER TABLE pagos ADD COLUMN monto_abonado DECIMAL(10, 2);
ALTER TABLE pagos ADD COLUMN registrado_por VARCHAR(120);
ALTER TABLE pagos ADD COLUMN anulado_por VARCHAR(120);
ALTER TABLE pagos ADD COLUMN fecha_anulacion DATETIME(6);
ALTER TABLE pagos ADD COLUMN motivo_anulacion VARCHAR(255);

CREATE INDEX idx_pagos_fecha_estado ON pagos (fecha, estado_pago);
