package com.shopflow.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

/** Used when {@code spring.kafka.bootstrap-servers} is not set. */
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    @Override
    public void paymentSucceeded(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId) {
        log.info("event (log transport) PAYMENT_SUCCESS orderId={} paymentId={}", orderId, paymentId);
    }

    @Override
    public void paymentFailed(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId, String reason) {
        log.info("event (log transport) PAYMENT_FAILED orderId={} reason={}", orderId, reason);
    }

    @Override
    public void refundCompleted(UUID orderId, UUID paymentId, BigDecimal amount, UUID userId) {
        log.info("event (log transport) REFUND_COMPLETED orderId={}", orderId);
    }
}
