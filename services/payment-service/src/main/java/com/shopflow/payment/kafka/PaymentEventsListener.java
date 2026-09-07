package com.shopflow.payment.kafka;

import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.kafka.InboundEvent;
import com.shopflow.common.kafka.ProcessedEventStore;
import com.shopflow.common.kafka.Topics;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.payment.api.dto.PaymentResponse;
import com.shopflow.payment.event.PaymentEventPublisher;
import com.shopflow.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumes {@code payment-events}. Only acts on the command-style events {@code PAYMENT_REQUESTED}
 * and {@code REFUND_INITIATED}; its own outcome events are ignored.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);
    private static final String CONSUMER = "payment-svc";

    private final EventJson eventJson;
    private final ProcessedEventStore processedEvents;
    private final PaymentService payments;
    private final PaymentEventPublisher events;

    public PaymentEventsListener(EventJson eventJson, ProcessedEventStore processedEvents,
                                 PaymentService payments, PaymentEventPublisher events) {
        this.eventJson = eventJson;
        this.processedEvents = processedEvents;
        this.payments = payments;
        this.events = events;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = eventJson.parse(record.value());
        CorrelationId.set(event.correlationId().toString());
        try {
            switch (event.eventType()) {
                case PAYMENT_REQUESTED -> {
                    if (processedEvents.markProcessed(event.eventId(), CONSUMER)) {
                        charge(event);
                    }
                }
                case REFUND_INITIATED -> {
                    if (processedEvents.markProcessed(event.eventId(), CONSUMER)) {
                        doRefund(event);
                    }
                }
                default -> log.debug("payment-svc ignoring {}", event.eventType());
            }
        } finally {
            CorrelationId.clear();
        }
    }

    private void charge(InboundEvent event) {
        UUID orderId = event.uuid("orderId");
        UUID userId = event.uuid("userId");
        BigDecimal amount = new BigDecimal(event.text("amount"));
        String currency = event.text("currency");

        PaymentResponse result = payments.process(orderId, amount, currency);
        if ("CAPTURED".equals(result.status())) {
            events.paymentSucceeded(orderId, result.paymentId(), result.amount(), userId);
        } else {
            events.paymentFailed(orderId, result.paymentId(), result.amount(), userId, result.failureReason());
        }
    }

    private void doRefund(InboundEvent event) {
        UUID orderId = event.uuid("orderId");
        UUID userId = event.uuid("userId");
        var refund = payments.refund(orderId, event.text("reason"));
        events.refundCompleted(orderId, null, refund.amount(), userId);
    }
}
