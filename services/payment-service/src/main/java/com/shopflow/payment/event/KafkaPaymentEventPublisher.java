package com.shopflow.payment.event;

import com.shopflow.common.event.EventType;
import com.shopflow.common.kafka.DomainEventPublisher;
import com.shopflow.common.kafka.Topics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes to {@code payment-events} (key = orderId). */
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final String SOURCE = "payment-service";

    private final DomainEventPublisher publisher;

    public KafkaPaymentEventPublisher(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void paymentSucceeded(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId) {
        publisher.publish(Topics.PAYMENT_EVENTS, orderId.toString(), EventType.PAYMENT_SUCCESS, 1, SOURCE,
                payload(orderId, paymentId, amount, userId, null));
    }

    @Override
    public void paymentFailed(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId, String reason) {
        publisher.publish(Topics.PAYMENT_EVENTS, orderId.toString(), EventType.PAYMENT_FAILED, 1, SOURCE,
                payload(orderId, paymentId, amount, userId, reason));
    }

    @Override
    public void refundCompleted(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId) {
        publisher.publish(Topics.PAYMENT_EVENTS, orderId.toString(), EventType.REFUND_COMPLETED, 1, SOURCE,
                payload(orderId, paymentId, amount, userId, null));
    }

    private static Map<String, Object> payload(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", orderId.toString());
        map.put("paymentId", paymentId == null ? null : paymentId.toString());
        map.put("amount", amount == null ? null : amount.toPlainString());
        map.put("userId", userId == null ? null : userId.toString());
        map.put("at", Instant.now().toString());
        if (reason != null) {
            map.put("reason", reason);
        }
        return map;
    }
}
