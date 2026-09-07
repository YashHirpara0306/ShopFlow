package com.shopflow.inventory.api.dto;

import java.util.UUID;

public record StockResponse(
        UUID productId,
        long total,
        long available,
        long reserved
) {
}
