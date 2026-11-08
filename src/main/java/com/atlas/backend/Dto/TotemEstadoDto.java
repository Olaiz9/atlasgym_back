package com.atlas.backend.Dto;

import java.time.LocalDateTime;

/**
 * Estado de la tablet del Tótem para la app de los titulares.
 *
 * @param estado            EN_LINEA, SIN_SENAL (no avisa hace más de {@code minutosAlerta} en horario
 *                          de apertura), FUERA_DE_HORARIO (no avisa, pero el gimnasio está cerrado) o
 *                          NUNCA_CONECTADO (la tablet todavía no se vinculó).
 * @param ultimoLatido      hora de la última señal (null si nunca se conectó).
 * @param minutosSinSenal   minutos desde la última señal (null si nunca se conectó).
 * @param pendientesOffline ingresos guardados en la tablet sin subir, según su última señal.
 * @param minutosAlerta     a partir de cuántos minutos sin señal se considera caído.
 */
public record TotemEstadoDto(
        String estado,
        LocalDateTime ultimoLatido,
        Long minutosSinSenal,
        int pendientesOffline,
        int minutosAlerta
) {}
