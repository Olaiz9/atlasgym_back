package com.atlas.backend.Seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${atlas.jwt.secret}")
    private String secretKey;

    @Value("${atlas.jwt.expiration:86400000}")
    private long jwtExpiration;

    public String generarToken(Long usuarioId, String email, String rol, Long alumnoId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", usuarioId);
        claims.put("rol", rol);
        if (alumnoId != null) {
            claims.put("alumnoId", alumnoId);
        }
        return construirToken(claims, email, jwtExpiration);
    }

    public String generarToken(UserDetails userDetails, Long usuarioId, String rol, Long alumnoId) {
        return generarToken(usuarioId, userDetails.getUsername(), rol, alumnoId);
    }

    private String construirToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extraerEmail(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    public Long extraerUsuarioId(String token) {
        Claims claims = extraerTodosLosClaims(token);
        Object id = claims.get("id");
        if (id instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String extraerRol(String token) {
        Claims claims = extraerTodosLosClaims(token);
        return claims.get("rol", String.class);
    }

    public Long extraerAlumnoId(String token) {
        Claims claims = extraerTodosLosClaims(token);
        Object alumnoId = claims.get("alumnoId");
        if (alumnoId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public <T> T extraerClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extraerTodosLosClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean esTokenValido(String token, UserDetails userDetails) {
        final String email = extraerEmail(token);
        return (email.equals(userDetails.getUsername()) && !esTokenExpirado(token));
    }

    public boolean esTokenExpirado(String token) {
        return extraerFechaExpiracion(token).before(new Date());
    }

    private Date extraerFechaExpiracion(String token) {
        return extraerClaim(token, Claims::getExpiration);
    }

    private Claims extraerTodosLosClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
