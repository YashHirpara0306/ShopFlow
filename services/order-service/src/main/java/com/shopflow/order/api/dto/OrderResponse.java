package com.shopflow.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String status,
        String currency,
        BigDecimal totalAmount,
        String cancelReason,
        List<OrderItemResponse> items,
        List<OrderStatusHistoryResponse> history,
        Instant createdAt,
        Instant updatedAt
) {
}
