package com.shopflow.common.security;

import java.time.Instant;

/**
 * A freshly minted access token plus the metadata a client needs to use and refresh it.
 *
 * @param token            the compact JWT string
 * @param expiresAt        absolute expiry ({@code exp})
 * @param expiresInSeconds lifetime in seconds from now (what an OAuth-style {@code expires_in} carries)
 */
public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
}
