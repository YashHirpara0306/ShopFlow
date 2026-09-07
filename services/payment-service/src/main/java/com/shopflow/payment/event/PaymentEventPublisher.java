package com.shopflow.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Events Payment emits to {@code payment-events} (key = orderId). */
public interface PaymentEventPublisher {

    void paymentSucceeded(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId);

    void paymentFailed(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId, String reason);

    void refundCompleted(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId);
}
