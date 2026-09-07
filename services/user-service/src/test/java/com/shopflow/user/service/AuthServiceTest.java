package com.shopflow.user.service;

import com.shopflow.common.error.AuthenticationFailedException;
import com.shopflow.common.security.IssuedToken;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.user.api.dto.LoginRequest;
import com.shopflow.user.api.dto.TokenResponse;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;
import com.shopflow.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtIssuer jwtIssuer;
    @Mock RefreshTokenService refreshTokens;

    AuthService authService() {
        return new AuthService(users, passwordEncoder, jwtIssuer, refreshTokens);
    }

    private AppUser activeCustomer() {
        return AppUser.create("jordan@example.com", "stored-hash", "Jordan", EnumSet.of(Role.CUSTOMER));
    }

    private void stubTokenIssuing() {
        when(jwtIssuer.issueAccessToken(anyString(), any()))
                .thenReturn(new IssuedToken("access.jwt", Instant.now().plusSeconds(900), 900));
    }

    @Test
    void loginReturnsAccessAndRefreshTokensOnCorrectCredentials() {
        AppUser user = activeCustomer();
        when(users.findByEmailIgnoreCase("jordan@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        when(refreshTokens.issueForLogin(user.getId())).thenReturn("refresh-raw");
        stubTokenIssuing();

        TokenResponse response = authService().login(new LoginRequest("jordan@example.com", "secret"));

        assertThat(response.accessToken()).isEqualTo("access.jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-raw");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void loginWithAWrongPasswordFailsWithGenericMessage() {
        AppUser user = activeCustomer();
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService().login(new LoginRequest("jordan@example.com", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void loginWithAnUnknownEmailStillRunsAPasswordCheck() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService().login(new LoginRequest("ghost@example.com", "x")))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(passwordEncoder).matches(any(), any()); // timing-attack mitigation
    }

    @Test
    void loginIsRejectedForADisabledAccount() {
        AppUser user = activeCustomer();
        user.disable();
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService().login(new LoginRequest("jordan@example.com", "secret")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void refreshRotatesAndIssuesAFreshAccessToken() {
        AppUser user = activeCustomer();
        when(refreshTokens.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.RotationResult(user.getId(), "new-refresh"));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        stubTokenIssuing();

        TokenResponse response = authService().refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("access.jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshFailsIfTheUserNoLongerExists() {
        UUID gone = UUID.randomUUID();
        when(refreshTokens.rotate(any()))
                .thenReturn(new RefreshTokenService.RotationResult(gone, "new-refresh"));
        when(users.findById(gone)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService().refresh("old"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logoutDelegatesToRefreshTokenRevocation() {
        authService().logout("some-refresh");
        verify(refreshTokens).revoke("some-refresh");
    }
}
