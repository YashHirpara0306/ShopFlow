package com.shopflow.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentRequest(
        @NotNull UUID orderId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        String currency
) {
}
