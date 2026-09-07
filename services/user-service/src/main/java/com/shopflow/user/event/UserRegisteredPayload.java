package com.shopflow.user.event;

import java.time.Instant;
import java.util.UUID;

/** Payload of a {@code USER_REGISTERED} event. See docs/architecture/events/event-catalog.md. */
public record UserRegisteredPayload(
        UUID userId,
        String email,
        String displayName,
        Instant registeredAt
) {
}
