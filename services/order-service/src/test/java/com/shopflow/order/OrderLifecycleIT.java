package com.shopflow.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.order.client.CartClient;
import com.shopflow.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end against real Postgres + Flyway + Spring Security. Cart Service is stubbed. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "shopflow.jwt.secret=integration-secret-integration-secret-1234")
class OrderLifecycleIT {

    private static final CartClient.CartView NON_EMPTY = new CartClient.CartView(
            List.of(new CartClient.CartView.Line(UUID.randomUUID(), "Widget", new BigDecimal("50.00"), 2)),
            new BigDecimal("100.00"), "INR");
    private static final CartClient.CartView EMPTY = new CartClient.CartView(List.of(), BigDecimal.ZERO, "INR");

    static final AtomicReference<CartClient.CartView> CART = new AtomicReference<>(NON_EMPTY);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class StubCart {
        @Bean
        CartClient cartClient() {
            return new CartClient() {
                public CartView getCart(String bearer) {
                    return CART.get();
                }

                public void clear(String bearer) {
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired OrderRepository orders;

    private final String userId = UUID.randomUUID().toString();
    private final String otherUserId = UUID.randomUUID().toString();

    private String auth(String uid, String role) {
        return "Bearer " + jwtIssuer.issueAccessToken(uid, Set.of(role)).token();
    }

    @BeforeEach
    void reset() {
        CART.set(NON_EMPTY);
    }

    @AfterEach
    void wipe() {
        orders.deleteAllInBatch();
    }

    private JsonNode createOrder(String idemKey) throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .header("Authorization", auth(userId, "ROLE_CUSTOMER"))
                        .header("Idempotency-Key", idemKey))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void createSnapshotsTheCartAndAdvancesToInventoryPending() throws Exception {
        JsonNode order = createOrder("k-1");
        // create writes PENDING + ORDER_CREATED to the outbox, then advances to INVENTORY_PENDING (2 history rows)
        org.assertj.core.api.Assertions.assertThat(order.get("status").asText()).isEqualTo("INVENTORY_PENDING");
        org.assertj.core.api.Assertions.assertThat(order.get("totalAmount").asDouble()).isEqualTo(100.0);
        org.assertj.core.api.Assertions.assertThat(order.get("items")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(order.get("history")).hasSize(2);
    }

    @Test
    void repeatingTheSameIdempotencyKeyReturnsTheSameOrder() throws Exception {
        String first = createOrder("k-2").get("id").asText();
        String second = createOrder("k-2").get("id").asText();
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void aDifferentIdempotencyKeyCreatesANewOrder() throws Exception {
        createOrder("k-3a");
        createOrder("k-3b");
        org.assertj.core.api.Assertions.assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    void anEmptyCartIsRejected() throws Exception {
        CART.set(EMPTY);
        mvc.perform(post("/api/orders")
                        .header("Authorization", auth(userId, "ROLE_CUSTOMER"))
                        .header("Idempotency-Key", "k-4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void anOrderIsHiddenFromOtherUsersButVisibleToAdmin() throws Exception {
        String id = createOrder("k-5").get("id").asText();

        mvc.perform(get("/api/orders/{id}", id).header("Authorization", auth(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", auth(otherUserId, "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", auth(otherUserId, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cancellingWorksOnceThenConflicts() throws Exception {
        String id = createOrder("k-6").get("id").asText();

        mvc.perform(post("/api/orders/{id}/cancellation", id).header("Authorization", auth(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/orders/{id}/cancellation", id).header("Authorization", auth(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanWalkTheStateMachineButNotJumpIllegally() throws Exception {
        String id = createOrder("k-7").get("id").asText(); // already INVENTORY_PENDING after create
        String admin = auth(otherUserId, "ROLE_ADMIN");

        step(id, admin, "AWAITING_PAYMENT");
        step(id, admin, "CONFIRMED");

        // illegal: cannot go back to PENDING
        mvc.perform(put("/api/orders/{id}/status", id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());
    }

    private void step(String id, String adminAuth, String target) throws Exception {
        mvc.perform(put("/api/orders/{id}/status", id).header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\",\"reason\":\"saga step\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(target));
    }

    @Test
    void forcingStatusIsForbiddenForCustomers() throws Exception {
        String id = createOrder("k-8").get("id").asText();
        mvc.perform(put("/api/orders/{id}/status", id).header("Authorization", auth(userId, "ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVENTORY_PENDING\"}"))
                .andExpect(status().isForbidden());
    }
}
