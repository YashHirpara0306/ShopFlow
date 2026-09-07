package com.shopflow.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted refresh token. Only the SHA-256 hash of the opaque token is stored - a database leak
 * does not hand out usable tokens.
 *
 * <p>Rotation: each successful refresh issues a new token in the same {@code familyId}, marks the
 * old one {@code revoked} and points {@code replacedById} at the new one. Presenting an already
 * rotated/revoked token is treated as theft and revokes the whole family.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    protected RefreshToken() {
        // for JPA
    }

    private RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.issuedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    /** Start a new rotation family (issued at login). */
    public static RefreshToken newFamily(UUID userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(userId, tokenHash, UUID.randomUUID(), expiresAt);
    }

    /** Next token in an existing family (issued at refresh). */
    public RefreshToken rotate(String newTokenHash, Instant newExpiresAt) {
        RefreshToken next = new RefreshToken(this.userId, newTokenHash, this.familyId, newExpiresAt);
        this.revoked = true;
        this.replacedById = next.id;
        return next;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !revoked && !isExpired();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public UUID getReplacedById() {
        return replacedById;
    }
}
