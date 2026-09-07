package com.shopflow.cart.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code quantity = 0} removes the line. */
public record UpdateQuantityRequest(
        @Min(0) @Max(99) int quantity
) {
}
