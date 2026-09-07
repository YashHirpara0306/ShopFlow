package com.shopflow.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Signs access tokens. Lives next to {@link JwtValidator} on purpose: the claim names
 * ({@code sub}, {@code roles}, {@code iss}) and the signing algorithm are defined once, so issuer
 * and validator can never drift apart.
 *
 * <p>Only User Service creates a bean of this type (it is the only issuer). Everyone else uses
 * {@link JwtValidator}.
 */
public class JwtIssuer {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtIssuer(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "shopflow.jwt.secret must be set and at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = properties.getIssuer();
        this.accessTokenTtl = properties.getAccessTokenTtl();
    }

    /**
     * @param userId the value for {@code sub}
     * @param roles  authority names, e.g. {@code ["ROLE_CUSTOMER"]}, stored in the {@code roles} claim
     */
    public IssuedToken issueAccessToken(String userId, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String jwt = Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("roles", List.copyOf(roles))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(jwt, expiresAt, accessTokenTtl.toSeconds());
    }
}
