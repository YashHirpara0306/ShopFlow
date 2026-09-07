package com.shopflow.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String status,
        String failureReason,
        Instant createdAt
) {
}
