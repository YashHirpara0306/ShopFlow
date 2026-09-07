package com.shopflow.inventory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID orderId,
        String status,
        List<Line> lines,
        Instant createdAt
) {
    public record Line(UUID productId, long quantity) {
    }
}
