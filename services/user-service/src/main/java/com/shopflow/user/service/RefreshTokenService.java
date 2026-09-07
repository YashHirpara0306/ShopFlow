package com.shopflow.user.service;

import com.shopflow.common.error.AuthenticationFailedException;
import com.shopflow.common.security.JwtProperties;
import com.shopflow.user.domain.RefreshToken;
import com.shopflow.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates, and revokes opaque refresh tokens.
 *
 * <p>The raw token is 256 bits of randomness, Base64URL-encoded, returned to the client once and
 * never stored. Only its SHA-256 hash is persisted. Rotation-with-reuse-detection: refreshing
 * revokes the presented token and issues a new one in the same family; presenting an
 * already-revoked token revokes the entire family (assumed stolen).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.ttl = jwtProperties.getRefreshTokenTtl();
    }

    public record RotationResult(UUID userId, String rawRefreshToken) {
    }

    @Transactional
    public String issueForLogin(UUID userId) {
        String raw = generateRawToken();
        repository.save(RefreshToken.newFamily(userId, sha256(raw), Instant.now().plus(ttl)));
        return raw;
    }

    /**
     * {@code noRollbackFor}: the reuse-detection branch revokes the token family and then signals
     * failure by throwing. That revocation must be committed, not rolled back with the exception.
     * The other failure branches write nothing, so committing an empty transaction is harmless.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public RotationResult rotate(String presentedRawToken) {
        RefreshToken current = repository.findByTokenHash(sha256(presentedRawToken))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        if (current.isRevoked()) {
            log.warn("refresh token reuse detected familyId={} userId={} - revoking family",
                    current.getFamilyId(), current.getUserId());
            repository.revokeFamily(current.getFamilyId());
            throw new AuthenticationFailedException("Refresh token reuse detected");
        }
        if (current.isExpired()) {
            throw new AuthenticationFailedException("Refresh token expired");
        }

        String newRaw = generateRawToken();
        RefreshToken next = current.rotate(sha256(newRaw), Instant.now().plus(ttl));
        repository.save(current); // revoked + replacedBy
        repository.save(next);
        return new RotationResult(current.getUserId(), newRaw);
    }

    /** Idempotent: an unknown or already-revoked token is a no-op. */
    @Transactional
    public void revoke(String presentedRawToken) {
        repository.findByTokenHash(sha256(presentedRawToken)).ifPresent(RefreshToken::revoke);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
