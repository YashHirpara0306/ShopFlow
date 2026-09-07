package com.shopflow.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.kafka.Topics;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.order.client.CartClient;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The orchestrated Saga, end to end, over real Kafka + Postgres (Testcontainers).
 * Feeds inventory/payment outcome events and asserts the order advances and the next command
 * event is emitted (via the transactional outbox). Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        "shopflow.outbox.relay-interval=200ms",
        "shopflow.order.watchdog-interval=3600s"
})
class OrderSagaOrchestratorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    static final CartClient.CartView CART = new CartClient.CartView(
            List.of(new CartClient.CartView.Line(UUID.randomUUID(), "Widget", new BigDecimal("50.00"), 2)),
            new BigDecimal("100.00"), "INR");

    @TestConfiguration
    static class Stubs {
        @Bean
        CartClient cartClient() {
            return new CartClient() {
                public CartView getCart(String bearer) {
                    return CART;
                }

                public void clear(String bearer) {
                }
            };
        }

        @Bean
        OutboundEvents outboundEvents() {
            return new OutboundEvents();
        }
    }

    static class OutboundEvents {
        final BlockingQueue<JsonNode> orderEvents = new LinkedBlockingQueue<>();
        final BlockingQueue<JsonNode> paymentEvents = new LinkedBlockingQueue<>();
        private final ObjectMapper mapper = new ObjectMapper();

        @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "test-order-events")
        void order(ConsumerRecord<String, String> r) throws Exception {
            orderEvents.add(mapper.readTree(r.value()));
        }

        @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "test-payment-events")
        void payment(ConsumerRecord<String, String> r) throws Exception {
            paymentEvents.add(mapper.readTree(r.value()));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderRepository orders;
    @Autowired OutboundEvents outbound;

    @AfterEach
    void wipe() {
        orders.deleteAllInBatch();
        outbound.orderEvents.clear();
        outbound.paymentEvents.clear();
    }

    private String customerAuth() {
        return "Bearer " + jwtIssuer.issueAccessToken(UUID.randomUUID().toString(), Set.of("ROLE_CUSTOMER")).token();
    }

    private UUID createOrder() throws Exception {
        String body = mvc.perform(post("/api/orders")
                        .header("Authorization", customerAuth())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void publish(String topic, UUID orderId, String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "eventVersion", 1,
                "timestamp", java.time.Instant.now().toString(),
                "correlationId", UUID.randomUUID().toString(),
                "source", "test",
                "payload", payload);
        try {
            kafkaTemplate.send(topic, orderId.toString(), objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void happyPathAdvancesToConfirmedAndEmitsTheRightCommands() throws Exception {
        UUID orderId = createOrder();

        // create() enqueued ORDER_CREATED; the relay publishes it
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                outbound.orderEvents.stream().anyMatch(e -> e.get("eventType").asText().equals("ORDER_CREATED")));

        // Inventory reserved -> order should move to AWAITING_PAYMENT and emit PAYMENT_REQUESTED
        publish(Topics.INVENTORY_EVENTS, orderId, "INVENTORY_RESERVED", Map.of("orderId", orderId.toString()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT));
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                outbound.paymentEvents.stream().anyMatch(e -> e.get("eventType").asText().equals("PAYMENT_REQUESTED")));

        // Payment succeeded -> CONFIRMED + ORDER_CONFIRMED
        publish(Topics.PAYMENT_EVENTS, orderId, "PAYMENT_SUCCESS", Map.of("orderId", orderId.toString()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED));
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                outbound.orderEvents.stream().anyMatch(e -> e.get("eventType").asText().equals("ORDER_CONFIRMED")));
    }

    @Test
    void inventoryShortageCancelsTheOrderWithoutRequestingPayment() throws Exception {
        UUID orderId = createOrder();

        publish(Topics.INVENTORY_EVENTS, orderId, "INVENTORY_RESERVATION_FAILED",
                Map.of("orderId", orderId.toString(), "reason", "OUT_OF_STOCK"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED));
        // no PAYMENT_REQUESTED was emitted
        assertThat(outbound.paymentEvents.stream()
                .anyMatch(e -> e.get("eventType").asText().equals("PAYMENT_REQUESTED"))).isFalse();
    }

    @Test
    void aDuplicateInventoryReservedEventIsIgnored() throws Exception {
        UUID orderId = createOrder();

        Map<String, Object> payload = Map.of("orderId", orderId.toString());
        Map<String, Object> envelope = Map.of(
                "eventId", UUID.randomUUID().toString(), "eventType", "INVENTORY_RESERVED", "eventVersion", 1,
                "timestamp", java.time.Instant.now().toString(), "correlationId", UUID.randomUUID().toString(),
                "source", "test", "payload", payload);
        String json = objectMapper.writeValueAsString(envelope);

        kafkaTemplate.send(Topics.INVENTORY_EVENTS, orderId.toString(), json);
        kafkaTemplate.send(Topics.INVENTORY_EVENTS, orderId.toString(), json); // exact duplicate (same eventId)

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT));

        Thread.sleep(2000); // let any duplicate processing settle
        long paymentRequests = outbound.paymentEvents.stream()
                .filter(e -> e.get("eventType").asText().equals("PAYMENT_REQUESTED")).count();
        assertThat(paymentRequests).isEqualTo(1);
    }
}
