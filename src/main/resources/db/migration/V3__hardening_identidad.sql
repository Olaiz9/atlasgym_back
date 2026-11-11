-- ============================================================================
-- ATLAS Gym - Flyway V3: Hardening de Identidad (2 Dueños Nominales y Cambio de Clave Inicial)
-- ============================================================================

ALTER TABLE usuarios ADD COLUMN nombre_mostrar VARCHAR(120);
ALTER TABLE usuarios ADD COLUMN requiere_cambio_password BOOLEAN NOT NULL DEFAULT FALSE;
