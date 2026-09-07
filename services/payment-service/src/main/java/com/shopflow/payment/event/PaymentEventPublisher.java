package com.shopflow.payment.event;

import com.shopflow.payment.domain.Payment;

/** Seam for payment domain events. Phase 9 logs them; Phase 11 publishes to {@code payment-events}. */
public interface PaymentEventPublisher {

    void paymentInitiated(Payment payment);

    void paymentSucceeded(Payment payment);

    void paymentFailed(Payment payment);

    void refundCompleted(java.util.UUID orderId, java.util.UUID paymentId, java.math.BigDecimal amount);
}
