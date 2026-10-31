-- ============================================================================
-- ATLAS Gym - Flyway V2: Idempotencia Offline del Tótem e Índice de Asistencias por Fecha
-- ============================================================================

ALTER TABLE asistencias ADD COLUMN client_event_id VARCHAR(64);
ALTER TABLE asistencias ADD CONSTRAINT uk_asistencias_client_event_id UNIQUE (client_event_id);
CREATE INDEX idx_asistencias_fecha_hora ON asistencias (fecha, hora);
