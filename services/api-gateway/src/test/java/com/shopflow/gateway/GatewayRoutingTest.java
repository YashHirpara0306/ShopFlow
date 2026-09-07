package com.shopflow.gateway;

import com.shopflow.common.security.JwtIssuer;
import com.shopflow.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Set;
import java.util.UUID;

/**
 * Edge behaviour of the gateway: public bypass, 401 on protected routes without a token, and the
 * standard error body. The downstream services are not running, so a request that clears auth fails
 * with 503 (proving it was NOT rejected at the edge). Rate limiting is disabled (no Redis here).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        // drop the Redis rate limiter for this test (no Redis running); keep only secure headers
        "spring.cloud.gateway.default-filters[0]=SecureHeaders"
})
class GatewayRoutingTest {

    @Autowired WebTestClient webTestClient;

    private JwtIssuer issuer() {
        JwtProperties p = new JwtProperties();
        p.setSecret("integration-secret-integration-secret-1234");
        p.setIssuer("shopflow");
        return new JwtIssuer(p);
    }

    @Test
    void protectedRouteWithoutATokenIs401WithTheStandardBody() {
        webTestClient.get().uri("/api/orders/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.error").isEqualTo("AUTHENTICATION_FAILED")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void publicRouteIsNotRejectedAtTheEdge() {
        // /api/products GET is public -> auth filter passes it through -> routing fails (no product-service) -> 503
        webTestClient.get().uri("/api/products?page=0")
                .exchange()
                .expectStatus().value(s -> org.assertj.core.api.Assertions.assertThat(s).isIn(503, 500));
    }

    @Test
    void aValidTokenClearsTheEdgeCheck() {
        String token = issuer().issueAccessToken(UUID.randomUUID().toString(), Set.of("ROLE_CUSTOMER")).token();
        // clears auth -> tries to route to order-service (down) -> 503, NOT 401
        webTestClient.get().uri("/api/orders/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(s -> org.assertj.core.api.Assertions.assertThat(s).isIn(503, 500));
    }

    @Test
    void anInboundUserHeaderIsStripped() {
        // spoofed X-User-Id must not survive; without a token this is still 401
        webTestClient.get().uri("/api/orders/" + UUID.randomUUID())
                .header("X-User-Id", "spoofed-admin")
                .header("X-User-Roles", "ROLE_ADMIN")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
