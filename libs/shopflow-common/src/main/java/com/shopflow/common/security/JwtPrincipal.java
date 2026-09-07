package com.shopflow.common.security;

import java.time.Instant;
import java.util.Set;

/**
 * The verified identity extracted from a valid access token.
 *
 * @param userId    the {@code sub} claim - the user's id
 * @param roles     authorities from the {@code roles} claim, e.g. {@code ["ROLE_CUSTOMER"]}
 * @param issuedAt  the {@code iat} claim
 * @param expiresAt the {@code exp} claim
 */
public record JwtPrincipal(
        String userId,
        Set<String> roles,
        Instant issuedAt,
        Instant expiresAt
) {

    public JwtPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
