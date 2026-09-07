package com.shopflow.order.event;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderEventPublisher.class);
    private static final String SOURCE = "order-service";

    @Override
    public void orderCreated(Order order) {
        emit(EventType.ORDER_CREATED, order);
    }

    @Override
    public void orderConfirmed(Order order) {
        emit(EventType.ORDER_CONFIRMED, order);
    }

    @Override
    public void orderCancelled(Order order, String reason) {
        emit(EventType.ORDER_CANCELLED, order);
    }

    private void emit(EventType type, Order order) {
        EventEnvelope<?> envelope = EventEnvelope.of(type, 1, correlationId(), SOURCE,
                new OrderRef(order.getId(), order.getUserId(), order.getStatus().name(), order.getTotalAmount()));
        log.info("event published type={} eventId={} orderId={} status={} (transport=log; Kafka in Phase 11)",
                envelope.eventType(), envelope.eventId(), order.getId(), order.getStatus());
    }

    private static UUID correlationId() {
        String current = CorrelationId.current();
        try {
            return current != null ? UUID.fromString(current) : UUID.randomUUID();
        } catch (IllegalArgumentException notAUuid) {
            return UUID.randomUUID();
        }
    }

    private record OrderRef(UUID orderId, UUID userId, String status, java.math.BigDecimal totalAmount) {
    }
}
