package com.shopflow.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerTest {

    private JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setSecret("0123456789-0123456789-0123456789-abc");
        p.setIssuer("shopflow");
        p.setAccessTokenTtl(Duration.ofMinutes(15));
        return p;
    }

    @Test
    void issuedTokenIsAcceptedByTheValidatorWithTheSameClaims() {
        JwtIssuer issuer = new JwtIssuer(props());
        JwtValidator validator = new JwtValidator(props());

        IssuedToken issued = issuer.issueAccessToken("user-42", List.of("ROLE_CUSTOMER"));
        JwtPrincipal principal = validator.validate(issued.token());

        assertThat(principal.userId()).isEqualTo("user-42");
        assertThat(principal.roles()).containsExactly("ROLE_CUSTOMER");
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        assertThat(issued.expiresAt()).isBetween(Instant.now().plusSeconds(880), Instant.now().plusSeconds(920));
    }

    @Test
    void carriesEveryRole() {
        IssuedToken issued = new JwtIssuer(props())
                .issueAccessToken("u1", Set.of("ROLE_CUSTOMER", "ROLE_ADMIN"));

        JwtPrincipal principal = new JwtValidator(props()).validate(issued.token());
        assertThat(principal.roles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void refusesAShortSecret() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("nope");
        assertThatThrownBy(() -> new JwtIssuer(weak)).isInstanceOf(IllegalStateException.class);
    }
}
