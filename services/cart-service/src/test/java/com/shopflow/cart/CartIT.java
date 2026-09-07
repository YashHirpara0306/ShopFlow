package com.shopflow.cart;

import com.shopflow.cart.client.ProductClient;
import com.shopflow.common.security.JwtIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end against a real Redis. Product Service is stubbed (its own tests cover it). Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        "shopflow.cart.ttl=1h"
})
class CartIT {

    static final UUID KNOWN_PRODUCT = UUID.randomUUID();
    static final UUID GONE_PRODUCT = UUID.randomUUID();

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class StubProduct {
        @Bean
        ProductClient productClient() {
            return productId -> productId.equals(KNOWN_PRODUCT)
                    ? Optional.of(new ProductClient.ProductSnapshot(KNOWN_PRODUCT, "Widget", new BigDecimal("25.00"), "INR", true))
                    : Optional.empty();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired StringRedisTemplate redisTemplate;

    private final String userId = UUID.randomUUID().toString();

    private String auth() {
        return "Bearer " + jwtIssuer.issueAccessToken(userId, Set.of("ROLE_CUSTOMER")).token();
    }

    @AfterEach
    void wipe() {
        Set<String> keys = redisTemplate.keys("cart:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void fullCartLifecycle() throws Exception {
        // add
        mvc.perform(post("/api/cart/items").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(KNOWN_PRODUCT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(50.00));

        // key exists with a TTL
        assertThat(redisTemplate.hasKey("cart:" + userId)).isTrue();
        assertThat(redisTemplate.getExpire("cart:" + userId)).isPositive();

        // increment
        mvc.perform(post("/api/cart/items").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(KNOWN_PRODUCT)))
                .andExpect(jsonPath("$.totalQuantity").value(3));

        // set exact quantity
        mvc.perform(put("/api/cart/items/{id}", KNOWN_PRODUCT).header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        // remove
        mvc.perform(delete("/api/cart/items/{id}", KNOWN_PRODUCT).header("Authorization", auth()))
                .andExpect(jsonPath("$.totalQuantity").value(0));

        // clear
        mvc.perform(delete("/api/cart").header("Authorization", auth()))
                .andExpect(status().isNoContent());
    }

    @Test
    void addingAnUnknownProductIsRejected() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(GONE_PRODUCT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void cartEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void staleLinesArePrunedOnRead() throws Exception {
        // put a line for a product the stub says is gone, directly into Redis
        redisTemplate.opsForHash().put("cart:" + userId, GONE_PRODUCT.toString(), "3");

        mvc.perform(get("/api/cart").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        assertThat(redisTemplate.<String, String>opsForHash().entries("cart:" + userId)).doesNotContainKey(GONE_PRODUCT.toString());
    }
}
