package com.shopflow.user.service;

import com.shopflow.common.error.AuthenticationFailedException;
import com.shopflow.common.security.JwtProperties;
import com.shopflow.user.domain.RefreshToken;
import com.shopflow.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repository;

    RefreshTokenService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setRefreshTokenTtl(Duration.ofDays(7));
        service = new RefreshTokenService(repository, props);
    }

    @Test
    void issueForLoginStoresOnlyTheHashNotTheRawToken() {
        String raw = service.issueForLogin(userId);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(raw).isNotBlank();
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.sha256(raw))
                .isNotEqualTo(raw);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void rotateRevokesTheOldTokenAndReturnsANewOneInTheSameFamily() {
        RefreshToken current = RefreshToken.newFamily(userId, "hash-of-old", Instant.now().plusSeconds(3600));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.RotationResult result = service.rotate("old-raw");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedById()).isNotNull();
        verify(repository, times(2)).save(any(RefreshToken.class)); // current + next
    }

    @Test
    void rotateRejectsAnUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rotateTreatsReuseOfARevokedTokenAsTheftAndRevokesTheWholeFamily() {
        RefreshToken revoked = RefreshToken.newFamily(userId, "hash", Instant.now().plusSeconds(3600));
        revoked.revoke();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("stolen"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("reuse");
        verify(repository).revokeFamily(revoked.getFamilyId());
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshToken expired = RefreshToken.newFamily(userId, "hash", Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void revokeIsANoOpForAnUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("whatever"); // must not throw
    }
}
