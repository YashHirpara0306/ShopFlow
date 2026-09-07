package com.shopflow.order.kafka;

import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.kafka.InboundEvent;
import com.shopflow.common.kafka.ProcessedEventStore;
import com.shopflow.common.kafka.Topics;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The orchestrator's inbound side: consumes inventory and payment outcomes and advances the Saga.
 * Idempotent (de-dupes on {@code eventId} in the same tx as the state change) and guarded (a late or
 * duplicate outcome for the wrong state is ignored by {@link OrderService}).
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class OrderSagaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaListener.class);
    private static final String CONSUMER = "order-svc";

    private final EventJson eventJson;
    private final ProcessedEventStore processedEvents;
    private final OrderService orderService;

    public OrderSagaListener(EventJson eventJson, ProcessedEventStore processedEvents, OrderService orderService) {
        this.eventJson = eventJson;
        this.processedEvents = processedEvents;
        this.orderService = orderService;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        handle(record.value());
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        handle(record.value());
    }

    private void handle(String json) {
        InboundEvent event = eventJson.parse(json);
        CorrelationId.set(event.correlationId().toString());
        try {
            if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
                return; // duplicate delivery
            }
            UUID orderId = event.uuid("orderId");
            switch (event.eventType()) {
                case INVENTORY_RESERVED -> orderService.onInventoryReserved(orderId);
                case INVENTORY_RESERVATION_FAILED -> orderService.onInventoryReservationFailed(orderId, event.text("reason"));
                case PAYMENT_SUCCESS -> orderService.onPaymentSucceeded(orderId);
                case PAYMENT_FAILED -> orderService.onPaymentFailed(orderId, event.text("reason"));
                case INVENTORY_RELEASED, PAYMENT_INITIATED -> log.debug("saga note {} for order {}", event.eventType(), orderId);
                default -> log.debug("order-svc ignoring {} ", event.eventType());
            }
        } finally {
            CorrelationId.clear();
        }
    }
}
