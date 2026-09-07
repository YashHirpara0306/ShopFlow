package com.shopflow.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.payment.repository.PaymentRepository;
import com.shopflow.payment.repository.RefundRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + Flyway + Security. fail-rate is forced to 0 so success is deterministic; the
 * ".13" rule is used for the deterministic failure case. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        "shopflow.payment.fail-rate=0.0"
})
class PaymentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired PaymentRepository payments;
    @Autowired RefundRepository refunds;

    private String admin() {
        return "Bearer " + jwtIssuer.issueAccessToken("admin-1", Set.of("ROLE_ADMIN")).token();
    }

    @AfterEach
    void wipe() {
        refunds.deleteAllInBatch();
        payments.deleteAllInBatch();
    }

    private JsonNode process(UUID orderId, String amount) throws Exception {
        String json = mvc.perform(post("/api/payments").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":%s,\"currency\":\"INR\"}".formatted(orderId, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void aNormalAmountIsCaptured() throws Exception {
        UUID orderId = UUID.randomUUID();
        assertThat(process(orderId, "100.00").get("status").asText()).isEqualTo("CAPTURED");

        mvc.perform(get("/api/payments/{orderId}", orderId).header("Authorization", admin()))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void anAmountEndingInThirteenAlwaysFails() throws Exception {
        JsonNode payment = process(UUID.randomUUID(), "120.13");
        assertThat(payment.get("status").asText()).isEqualTo("FAILED");
        assertThat(payment.get("failureReason").asText()).isEqualTo("DECLINED");
    }

    @Test
    void processingTheSameOrderTwiceDoesNotChargeAgain() throws Exception {
        UUID orderId = UUID.randomUUID();
        String firstId = process(orderId, "50.00").get("paymentId").asText();
        String secondId = process(orderId, "50.00").get("paymentId").asText();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void refundOfACapturedPaymentCompletesAndIsIdempotent() throws Exception {
        UUID orderId = UUID.randomUUID();
        process(orderId, "75.00");

        mvc.perform(post("/api/payments/{orderId}/refund", orderId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(post("/api/payments/{orderId}/refund", orderId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void refundOfAFailedPaymentIsRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        process(orderId, "10.13"); // fails

        mvc.perform(post("/api/payments/{orderId}/refund", orderId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
}
