package com.shopflow.cart.client;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Read-only view of a product, needed to validate cart items and price the cart. */
public interface ProductClient {

    Optional<ProductSnapshot> findActive(UUID productId);

    record ProductSnapshot(UUID id, String name, BigDecimal price, String currency, boolean active) {
    }
}
