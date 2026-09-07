package com.shopflow.order.kafka;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.observability.CorrelationId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enqueues an event into the outbox <em>inside the caller's transaction</em>. The event is published
 * to Kafka later by {@link OutboxRelay}. Same transaction as the order state change => no dual-write gap.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class OutboxWriter {

    private static final String SOURCE = "order-service";

    private final OutboxRepository outbox;
    private final EventJson eventJson;

    public OutboxWriter(OutboxRepository outbox, EventJson eventJson) {
        this.outbox = outbox;
        this.eventJson = eventJson;
    }

    public void enqueue(String topic, String partitionKey, EventType type, int version, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, version, correlationId(), SOURCE, payload);
        outbox.save(new OutboxEvent(topic, partitionKey, eventJson.write(envelope)));
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
