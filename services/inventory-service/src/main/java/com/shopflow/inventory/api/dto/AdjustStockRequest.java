package com.shopflow.inventory.api.dto;

import jakarta.validation.constraints.Size;

/** {@code delta} may be negative (to reduce on-hand quantity). */
public record AdjustStockRequest(
        long delta,
        @Size(max = 200) String reason
) {
}
