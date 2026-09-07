package com.shopflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies an access token's signature, issuer, and expiry and returns the {@link JwtPrincipal}.
 *
 * <p>Stateless: constructed once per service from {@link JwtProperties}, then shared. Used by the
 * API Gateway (to authenticate at the edge) and by every service (defence in depth - services do
 * not blindly trust the gateway's {@code X-User-*} headers).
 *
 * <p>Any verification failure - bad signature, wrong issuer, expired, malformed - surfaces as a
 * single {@link InvalidTokenException} (HTTP 401). The underlying jjwt exception type is not leaked
 * to callers.
 */
public class JwtValidator {

    private final SecretKey key;
    private final String issuer;
    private final long clockSkewSeconds;

    public JwtValidator(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "shopflow.jwt.secret must be set and at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.getIssuer();
        this.clockSkewSeconds = Math.max(0, properties.getClockSkew().toSeconds());
    }

    public JwtPrincipal validate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing token");
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token);

            Claims claims = jws.getPayload();
            return new JwtPrincipal(
                    claims.getSubject(),
                    extractRoles(claims.get("roles")),
                    claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid token (" + ex.getClass().getSimpleName() + ")", ex);
        }
    }

    /** Accepts the {@code roles} claim as a JSON array or a comma/space-separated string. */
    private static Set<String> extractRoles(Object claim) {
        if (claim instanceof Collection<?> collection) {
            Set<String> roles = new LinkedHashSet<>();
            for (Object element : collection) {
                roles.add(String.valueOf(element));
            }
            return roles;
        }
        if (claim instanceof String s && !s.isBlank()) {
            return new LinkedHashSet<>(List.of(s.split("[,\\s]+")));
        }
        return Set.of();
    }
}
