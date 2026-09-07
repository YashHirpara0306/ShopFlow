package com.shopflow.inventory.event;

import com.shopflow.common.event.EventType;
import com.shopflow.common.kafka.DomainEventPublisher;
import com.shopflow.common.kafka.Topics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes to {@code inventory-events} (key = orderId). */
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private static final String SOURCE = "inventory-service";

    private final DomainEventPublisher publisher;

    public KafkaInventoryEventPublisher(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void reserved(UUID orderId, Map<UUID, Long> quantities) {
        publisher.publish(Topics.INVENTORY_EVENTS, orderId.toString(), EventType.INVENTORY_RESERVED, 1, SOURCE,
                Map.of("orderId", orderId.toString(), "reservedAt", Instant.now().toString(),
                        "lineCount", quantities.size()));
    }

    @Override
    public void reservationFailed(UUID orderId, List<String> shortages) {
        publisher.publish(Topics.INVENTORY_EVENTS, orderId.toString(), EventType.INVENTORY_RESERVATION_FAILED, 1, SOURCE,
                Map.of("orderId", orderId.toString(), "failedAt", Instant.now().toString(),
                        "reason", "OUT_OF_STOCK", "shortages", shortages));
    }

    @Override
    public void released(UUID orderId) {
        publisher.publish(Topics.INVENTORY_EVENTS, orderId.toString(), EventType.INVENTORY_RELEASED, 1, SOURCE,
                Map.of("orderId", orderId.toString(), "releasedAt", Instant.now().toString(), "reason", "ORDER_CANCELLED"));
    }

    @Override
    public void confirmed(UUID orderId) {
        // Order Service does not wait on a "confirmed" event; nothing to publish.
    }
}
