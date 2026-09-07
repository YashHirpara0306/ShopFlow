package com.shopflow.order.api.dto;

import java.time.Instant;

public record OrderStatusHistoryResponse(
        String fromStatus,
        String toStatus,
        String reason,
        Instant at
) {
}
