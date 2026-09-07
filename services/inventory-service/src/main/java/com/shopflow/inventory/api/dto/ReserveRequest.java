package com.shopflow.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record ReserveRequest(
        @NotNull UUID orderId,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID productId,
            @Positive long quantity
    ) {
    }
}
