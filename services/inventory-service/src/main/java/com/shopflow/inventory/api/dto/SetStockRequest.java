package com.shopflow.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record SetStockRequest(
        @NotNull UUID productId,
        @PositiveOrZero long quantity
) {
}
