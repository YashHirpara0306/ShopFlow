package com.shopflow.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "0123456789-0123456789-0123456789-abc"; // >= 32 bytes
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        p.setIssuer("shopflow");
        return p;
    }

    private String token(String issuer, Instant expiry, SecretKey signingKey) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("user-1")
                .claim("roles", List.of("ROLE_CUSTOMER", "ROLE_ADMIN"))
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void acceptsAValidTokenAndExtractsPrincipal() {
        String jwt = token("shopflow", Instant.now().plusSeconds(120), KEY);

        JwtPrincipal principal = new JwtValidator(props()).validate(jwt);

        assertThat(principal.userId()).isEqualTo("user-1");
        assertThat(principal.roles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
        assertThat(principal.expiresAt()).isAfter(Instant.now());
        assertThat(principal.hasRole("ROLE_ADMIN")).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        String jwt = token("shopflow", Instant.now().minusSeconds(120), KEY);

        assertThatThrownBy(() -> new JwtValidator(props()).validate(jwt))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAWrongSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-secret-another-secret-1234".getBytes(StandardCharsets.UTF_8));
        String jwt = token("shopflow", Instant.now().plusSeconds(120), otherKey);

        assertThatThrownBy(() -> new JwtValidator(props()).validate(jwt))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAWrongIssuer() {
        String jwt = token("someone-else", Instant.now().plusSeconds(120), KEY);

        assertThatThrownBy(() -> new JwtValidator(props()).validate(jwt))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsNullOrBlankToken() {
        JwtValidator validator = new JwtValidator(props());
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> validator.validate("   ")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesToStartWithATooShortSecret() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("too-short");

        assertThatThrownBy(() -> new JwtValidator(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
