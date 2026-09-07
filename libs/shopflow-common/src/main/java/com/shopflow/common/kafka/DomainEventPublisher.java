package com.shopflow.common.kafka;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.observability.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Publishes an {@link EventEnvelope} as a JSON string to a topic with the given partition key.
 * Adds {@code X-Correlation-Id} and {@code eventType} headers. The producer is configured
 * idempotent + {@code acks=all} (see {@code KafkaSupportAutoConfiguration}).
 */
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventJson eventJson;

    public DomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate, EventJson eventJson) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventJson = eventJson;
    }

    /** Build an envelope (correlation id taken from the current MDC) and publish it. */
    public void publish(String topic, String partitionKey, EventType type, int version, String source, Object payload) {
        UUID correlationId = currentCorrelationId();
        EventEnvelope<Object> envelope = EventEnvelope.of(type, version, correlationId, source, payload);
        publish(topic, partitionKey, envelope);
    }

    public void publish(String topic, String partitionKey, EventEnvelope<?> envelope) {
        String json = eventJson.write(envelope);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, json);
        record.headers().add(new RecordHeader(CorrelationId.HEADER,
                envelope.correlationId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventType",
                envelope.eventType().name().getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);
        log.info("event published topic={} key={} type={} eventId={}", topic, partitionKey,
                envelope.eventType(), envelope.eventId());
    }

    private static UUID currentCorrelationId() {
        String current = CorrelationId.current();
        try {
            return current != null ? UUID.fromString(current) : UUID.randomUUID();
        } catch (IllegalArgumentException notAUuid) {
            return UUID.randomUUID();
        }
    }
}
