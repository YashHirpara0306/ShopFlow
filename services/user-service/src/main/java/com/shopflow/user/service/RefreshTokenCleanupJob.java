package com.shopflow.user.service;

import com.shopflow.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Prunes revoked and expired refresh tokens so the table does not grow without bound. */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${shopflow.security.refresh-token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purge() {
        int removed = refreshTokens.deleteRevokedOrExpiredBefore(Instant.now());
        if (removed > 0) {
            log.info("refresh-token cleanup removed {} revoked/expired rows", removed);
        }
    }
}
