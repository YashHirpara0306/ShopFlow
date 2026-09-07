package com.shopflow.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        String channel,
        String template,
        String status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant sentAt
) {
}
