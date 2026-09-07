package com.shopflow.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID orderId,
        BigDecimal amount,
        String status,
        Instant createdAt
) {
}
