package com.shopflow.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;

import java.time.Instant;
import java.util.UUID;

/** Serialises an {@link EventEnvelope} to a JSON string and parses an inbound record back to {@link InboundEvent}. */
public final class EventJson {

    private final ObjectMapper mapper;

    public EventJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(EventEnvelope<?> envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise event " + envelope.eventType(), e);
        }
    }

    public InboundEvent parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new InboundEvent(
                    UUID.fromString(root.get("eventId").asText()),
                    EventType.valueOf(root.get("eventType").asText()),
                    root.path("eventVersion").asInt(1),
                    Instant.parse(root.get("timestamp").asText()),
                    UUID.fromString(root.get("correlationId").asText()),
                    root.path("source").asText(null),
                    root.get("payload"));
        } catch (Exception e) {
            // Non-retryable: a malformed envelope will never parse. The error handler routes it to the DLT.
            throw new IllegalArgumentException("Malformed event envelope", e);
        }
    }
}
