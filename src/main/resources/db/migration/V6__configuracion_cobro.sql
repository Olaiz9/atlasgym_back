-- ============================================================================
-- ATLAS Gym - Flyway V6: Datos de cobro editables por los titulares
-- Alias / titular de la cuenta para transferencias. Cambian con el tiempo, por eso viven en la
-- base (editables desde Finanzas) y no hardcodeados ni en variables de build.
-- ============================================================================

CREATE TABLE configuracion_cobro (
    id BIGINT PRIMARY KEY,
    alias VARCHAR(100),
    titular VARCHAR(150),
    actualizado_por VARCHAR(120),
    actualizado_en DATETIME(6)
);

INSERT INTO configuracion_cobro (id, alias, titular, actualizado_por, actualizado_en)
VALUES (1, NULL, NULL, NULL, NULL);
