package com.shopflow.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The versioned wrapper that every Kafka message in ShopFlow is packaged in.
 *
 * <p>Every field earns its place (see {@code docs/architecture/phase-01-requirements-and-architecture.md}):
 * <ul>
 *   <li>{@code eventId}      - unique id for <em>this delivery of this event</em>; the idempotency key consumers de-duplicate on.</li>
 *   <li>{@code eventType}    - lets one topic carry several event kinds; consumers filter/switch on it.</li>
 *   <li>{@code eventVersion} - schema evolution; a v1-only consumer can branch or reject when it sees v2.</li>
 *   <li>{@code timestamp}    - when the business fact occurred (UTC); used for ordering sanity, latency metrics, debugging.</li>
 *   <li>{@code correlationId}- the id that flows Client -&gt; Gateway -&gt; Order -&gt; every event -&gt; every log line.</li>
 *   <li>{@code source}       - which service emitted it; audit + loop prevention.</li>
 *   <li>{@code payload}      - the event-type-specific body (minimal: ids + the few facts a consumer needs).</li>
 * </ul>
 *
 * <p>This is an immutable {@code record}. The compact constructor fails fast on a malformed envelope
 * so a bad message never gets published or acted upon.
 *
 * @param <T> the payload type for this event
 */
public record EventEnvelope<T>(
        UUID eventId,
        EventType eventType,
        int eventVersion,
        Instant timestamp,
        UUID correlationId,
        String source,
        T payload
) {

    public EventEnvelope {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1, was " + eventVersion);
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
    }

    /**
     * Build an envelope for a brand-new event: generates {@code eventId} and stamps {@code timestamp = now}.
     *
     * @param type          the business event type
     * @param version       the schema version of the payload (>= 1)
     * @param correlationId the correlation id propagated from the triggering request/event
     * @param source        the emitting service name, e.g. {@code "order-service"}
     * @param payload       the event body
     */
    public static <T> EventEnvelope<T> of(EventType type,
                                          int version,
                                          UUID correlationId,
                                          String source,
                                          T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, version, Instant.now(), correlationId, source, payload);
    }
}
