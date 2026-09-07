package com.shopflow.inventory.kafka;

import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.kafka.InboundEvent;
import com.shopflow.common.kafka.ProcessedEventStore;
import com.shopflow.common.kafka.Topics;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.inventory.event.InventoryEventPublisher;
import com.shopflow.inventory.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code order-events} and drives the inventory side of the Saga:
 * ORDER_CREATED -&gt; reserve, ORDER_CANCELLED -&gt; release (compensation), ORDER_CONFIRMED -&gt; confirm.
 * Idempotent via {@code processed_event} (same tx as the DB change).
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String CONSUMER = "inventory-svc";

    private final EventJson eventJson;
    private final ProcessedEventStore processedEvents;
    private final InventoryService inventory;
    private final InventoryEventPublisher events;

    public OrderEventsListener(EventJson eventJson, ProcessedEventStore processedEvents,
                               InventoryService inventory, InventoryEventPublisher events) {
        this.eventJson = eventJson;
        this.processedEvents = processedEvents;
        this.inventory = inventory;
        this.events = events;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = CONSUMER)
    @Transactional
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = eventJson.parse(record.value());
        CorrelationId.set(event.correlationId().toString());
        try {
            if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
                return;
            }
            UUID orderId = event.uuid("orderId");
            switch (event.eventType()) {
                case ORDER_CREATED -> reserve(orderId, event);
                case ORDER_CANCELLED -> {
                    if (inventory.hasReservation(orderId)) {
                        inventory.release(orderId);
                        events.released(orderId);
                    }
                }
                case ORDER_CONFIRMED -> {
                    if (inventory.hasReservation(orderId)) {
                        inventory.confirm(orderId);
                    }
                }
                default -> log.debug("inventory-svc ignoring {}", event.eventType());
            }
        } finally {
            CorrelationId.clear();
        }
    }

    private void reserve(UUID orderId, InboundEvent event) {
        Map<UUID, Long> quantities = new LinkedHashMap<>();
        event.payload().withArray("lines").forEach(line ->
                quantities.merge(UUID.fromString(line.get("productId").asText()),
                        line.get("quantity").asLong(), Long::sum));

        InventoryService.ReservationOutcome outcome = inventory.reserve(orderId, quantities);
        if (outcome.reserved()) {
            events.reserved(orderId, quantities);
        } else {
            events.reservationFailed(orderId, outcome.shortages());
        }
    }
}
