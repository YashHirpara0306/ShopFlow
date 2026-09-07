package com.shopflow.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID userId,
        List<CartLine> items,
        int totalQuantity,
        BigDecimal subtotal,
        String currency
) {
    public record CartLine(
            UUID productId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {
    }
}
