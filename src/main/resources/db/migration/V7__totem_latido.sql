-- ============================================================================
-- ATLAS Gym - Flyway V7: Señal de vida de la tablet del Tótem
-- La tablet avisa cada minuto que sigue andando. Si deja de avisar en horario de apertura,
-- la app de los titulares muestra "Tótem sin señal" (antes nadie se enteraba hasta que un
-- socio lo comentaba). Fila única: hay una sola tablet en el mostrador.
-- ============================================================================

CREATE TABLE totem_latido (
    id BIGINT PRIMARY KEY,
    ultimo_latido DATETIME(6),
    pendientes_offline INT NOT NULL DEFAULT 0
);

INSERT INTO totem_latido (id, ultimo_latido, pendientes_offline)
VALUES (1, NULL, 0);
