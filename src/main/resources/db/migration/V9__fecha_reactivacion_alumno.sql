-- ============================================================================
-- ATLAS Gym - Flyway V9: Fecha de reactivación del socio
-- Un socio que vuelve después de más de 60 días seguía en "Inactivos" aunque el titular lo
-- reactivara, porque la regla solo miraba la fecha de alta y los pagos. Al reactivarlo se guarda
-- la fecha y cuenta como un alta nueva para esa regla. Null = nunca se reactivó.
-- ============================================================================

ALTER TABLE alumnos ADD COLUMN fecha_reactivacion DATE NULL;
