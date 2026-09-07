package com.shopflow.product.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        String imageUrl,
        boolean active,
        Instant createdAt
) {
}
