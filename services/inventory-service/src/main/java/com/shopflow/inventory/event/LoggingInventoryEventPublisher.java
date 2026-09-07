package com.shopflow.inventory.event;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.observability.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LoggingInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingInventoryEventPublisher.class);
    private static final String SOURCE = "inventory-service";

    @Override
    public void reserved(UUID orderId, Map<UUID, Long> quantities) {
        emit(EventType.INVENTORY_RESERVED, orderId, "lines=" + quantities.size());
    }

    @Override
    public void reservationFailed(UUID orderId, List<String> shortages) {
        emit(EventType.INVENTORY_RESERVATION_FAILED, orderId, "shortages=" + shortages);
    }

    @Override
    public void released(UUID orderId) {
        emit(EventType.INVENTORY_RELEASED, orderId, "");
    }

    @Override
    public void confirmed(UUID orderId) {
        emit(EventType.INVENTORY_RESERVED, orderId, "confirmed");
    }

    private void emit(EventType type, UUID orderId, String detail) {
        EventEnvelope<?> envelope = EventEnvelope.of(type, 1, correlationId(), SOURCE, Map.of("orderId", orderId.toString()));
        log.info("event published type={} eventId={} orderId={} {} (transport=log; Kafka in Phase 11)",
                envelope.eventType(), envelope.eventId(), orderId, detail);
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
