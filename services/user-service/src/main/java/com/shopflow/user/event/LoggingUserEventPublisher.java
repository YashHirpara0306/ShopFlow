package com.shopflow.user.event;

import com.shopflow.common.event.EventEnvelope;
import com.shopflow.common.event.EventType;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 4 implementation: builds the real {@link EventEnvelope} and logs it. This proves the event
 * shape and the correlation-id propagation without a broker. Phase 11 swaps in a Kafka publisher.
 */
@Component
public class LoggingUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingUserEventPublisher.class);
    private static final String SOURCE = "user-service";

    @Override
    public void userRegistered(AppUser user) {
        UUID correlationId = currentCorrelationId();
        EventEnvelope<UserRegisteredPayload> event = EventEnvelope.of(
                EventType.USER_REGISTERED,
                1,
                correlationId,
                SOURCE,
                new UserRegisteredPayload(user.getId(), user.getEmail(), user.getDisplayName(), Instant.now()));

        log.info("event published type={} eventId={} userId={} (transport=log; Kafka in Phase 11)",
                event.eventType(), event.eventId(), user.getId());
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
