package com.shopflow.product.service;

import java.math.BigDecimal;

/** Parsed, validated filter arguments for the public product listing. */
public record ProductQuery(
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String search
) {
}
