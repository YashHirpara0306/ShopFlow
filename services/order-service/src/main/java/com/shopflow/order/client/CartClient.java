package com.shopflow.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Reads and clears the caller's cart in Cart Service. The caller's bearer token is forwarded. */
public interface CartClient {

    CartView getCart(String bearerToken);

    void clear(String bearerToken);

    record CartView(List<Line> items, BigDecimal subtotal, String currency) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }

        public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity) {
        }
    }
}
