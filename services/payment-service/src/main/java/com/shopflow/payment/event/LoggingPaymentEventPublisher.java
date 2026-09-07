package com.shopflow.payment.event;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.payment.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);
    private static final String SOURCE = "payment-service";

    @Override
    public void paymentInitiated(Payment payment) {
        emit(EventType.PAYMENT_INITIATED, payment.getOrderId(), payment.getId());
    }

    @Override
    public void paymentSucceeded(Payment payment) {
        emit(EventType.PAYMENT_SUCCESS, payment.getOrderId(), payment.getId());
    }

    @Override
    public void paymentFailed(Payment payment) {
        emit(EventType.PAYMENT_FAILED, payment.getOrderId(), payment.getId());
    }

    @Override
    public void refundCompleted(UUID orderId, UUID paymentId, BigDecimal amount) {
        emit(EventType.REFUND_COMPLETED, orderId, paymentId);
    }

    private void emit(EventType type, UUID orderId, UUID paymentId) {
        EventEnvelope<?> envelope = EventEnvelope.of(type, 1, correlationId(), SOURCE,
                Map.of("orderId", orderId.toString(), "paymentId", paymentId.toString()));
        log.info("event published type={} eventId={} orderId={} paymentId={} (transport=log; Kafka in Phase 11)",
                envelope.eventType(), envelope.eventId(), orderId, paymentId);
    }

    private static UUID correlationId() {
        String current = CorrelationId.current();
        try {
            return current != null ? UUID.fromString(current) : UUID.randomUUID();
        } catch (IllegalArgumentException notAUuid) {
            return UUID.randomUUID();
        }
    }
}
