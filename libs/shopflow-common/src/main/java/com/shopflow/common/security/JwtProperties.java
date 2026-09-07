package com.shopflow.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for issuing and validating JWTs, bound from {@code shopflow.jwt.*}.
 *
 * <p>The {@code secret} MUST come from an environment variable / secret store - never a checked-in
 * file. For HS256 it must be at least 32 bytes. User Service uses {@code secret} + the TTLs to
 * <em>issue</em> tokens; every other service uses {@code secret} + {@code issuer} to <em>validate</em>.
 */
@ConfigurationProperties(prefix = "shopflow.jwt")
public class JwtProperties {

    /** HMAC-SHA256 signing secret. Required. Minimum 32 bytes. Source: env var {@code JWT_SECRET}. */
    private String secret;

    /** Expected {@code iss} claim. */
    private String issuer = "shopflow";

    /** Access-token lifetime. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh-token lifetime. */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Tolerated clock difference between services when checking {@code exp}/{@code nbf}. */
    private Duration clockSkew = Duration.ofSeconds(30);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }
}
