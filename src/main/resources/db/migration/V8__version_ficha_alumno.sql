-- ============================================================================
-- ATLAS Gym - Flyway V8: Versión de la ficha del socio
-- Los dos titulares pueden tener la misma ficha abierta en sus celulares. Cada edición manda la
-- versión que vio; si el otro titular la cambió antes, el servidor responde 409 en lugar de pisar
-- sus cambios con datos viejos. No sube con el check-in del tótem (solo con cambios de un titular).
-- ============================================================================

ALTER TABLE alumnos ADD COLUMN version_datos BIGINT NOT NULL DEFAULT 0;
