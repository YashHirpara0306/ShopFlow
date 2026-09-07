package com.shopflow.user.service;

import com.shopflow.common.error.AuthenticationFailedException;
import com.shopflow.common.security.IssuedToken;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.user.api.dto.LoginRequest;
import com.shopflow.user.api.dto.TokenResponse;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;
import com.shopflow.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A real, well-formed BCrypt hash (the canonical Spring Security example hash for "password").
     * When the email is unknown we still run {@code matches()} against this so login latency does
     * not reveal whether an account exists. It must be a valid hash or BCrypt short-circuits.
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokens;

    public AuthService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);

        String hash = user != null ? user.getPasswordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordMatches) {
            throw new AuthenticationFailedException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new AuthenticationFailedException("Account is disabled");
        }

        log.info("login ok userId={}", user.getId());
        return issueTokens(user, refreshTokens.issueForLogin(user.getId()));
    }

    // noRollbackFor: rotate() may commit a family revocation before signalling failure - see RefreshTokenService.
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokens.rotate(rawRefreshToken);
        AppUser user = users.findById(rotation.userId())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));
        if (!user.isActive()) {
            throw new AuthenticationFailedException("Account is disabled");
        }
        return issueTokens(user, rotation.rawRefreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private TokenResponse issueTokens(AppUser user, String rawRefreshToken) {
        Set<String> authorities = user.getRoles().stream()
                .map(Role::authority)
                .collect(Collectors.toSet());
        IssuedToken access = jwtIssuer.issueAccessToken(user.getId().toString(), authorities);
        return TokenResponse.bearer(access.token(), rawRefreshToken, access.expiresInSeconds());
    }
}
