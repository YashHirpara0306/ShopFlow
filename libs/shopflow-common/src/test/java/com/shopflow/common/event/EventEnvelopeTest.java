package com.shopflow.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A real (not placeholder) unit test so `mvn verify` has something meaningful to run on the
 * empty skeleton, and so the shared contract's invariants are locked from Phase 2 onward.
 */
class EventEnvelopeTest {

    @Test
    void factoryGeneratesIdAndTimestampAndKeepsGivenFields() {
        UUID correlationId = UUID.randomUUID();

        EventEnvelope<String> event =
                EventEnvelope.of(EventType.ORDER_CREATED, 1, correlationId, "order-service", "payload");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.eventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.source()).isEqualTo("order-service");
        assertThat(event.payload()).isEqualTo("payload");
    }

    @Test
    void rejectsEventVersionBelowOne() {
        UUID correlationId = UUID.randomUUID();

        assertThatThrownBy(() ->
                EventEnvelope.of(EventType.ORDER_CREATED, 0, correlationId, "order-service", "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void rejectsBlankSource() {
        UUID correlationId = UUID.randomUUID();

        assertThatThrownBy(() ->
                EventEnvelope.of(EventType.ORDER_CREATED, 1, correlationId, "  ", "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }
}
