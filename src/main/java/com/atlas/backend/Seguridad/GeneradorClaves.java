package com.atlas.backend.Seguridad;

import java.security.SecureRandom;

/**
 * Claves temporales para los socios: al azar (SecureRandom), faciles de dictar o copiar por
 * WhatsApp (sin caracteres que se confunden como 0/O o 1/l/I) y de un solo uso, porque el
 * socio tiene que cambiarla en su primer ingreso (requiereCambioPassword).
 */
public final class GeneradorClaves {

    private static final String ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int LONGITUD = 8;
    private static final SecureRandom AZAR = new SecureRandom();

    private GeneradorClaves() {
    }

    public static String claveTemporal() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
