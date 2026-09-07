package com.shopflow.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Partial update. Null fields are left unchanged. */
public record UpdateProductRequest(
        @Size(min = 1, max = 200) String name,
        @Size(max = 5000) String description,
        @DecimalMin(value = "0.00", inclusive = false) BigDecimal price,
        UUID categoryId,
        @Size(max = 500) String imageUrl,
        Boolean active
) {
}
