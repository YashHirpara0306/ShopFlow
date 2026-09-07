package com.shopflow.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopflow.common.event.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * A received event, parsed just enough for a consumer to dispatch on {@link #eventType} and
 * de-duplicate on {@link #eventId}. The payload stays as a {@link JsonNode} so each handler reads
 * only the fields it needs (tolerant reader).
 */
public record InboundEvent(
        UUID eventId,
        EventType eventType,
        int eventVersion,
        Instant timestamp,
        UUID correlationId,
        String source,
        JsonNode payload
) {

    public String text(String field) {
        JsonNode n = payload.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    public UUID uuid(String field) {
        String v = text(field);
        return v == null ? null : UUID.fromString(v);
    }

    public long asLong(String field) {
        JsonNode n = payload.get(field);
        return n == null ? 0L : n.asLong();
    }
}
