package com.shopflow.cart.store;

import java.util.Map;
import java.util.UUID;

/**
 * The cart's persistence contract. The Redis implementation is the source of truth (ADR-0004);
 * this interface keeps {@code CartService} testable without a broker.
 */
public interface CartStore {

    /** Add {@code delta} to a line, clamped to {@code [0, max]}; returns the new quantity. Refreshes TTL. */
    int addQuantity(UUID userId, UUID productId, int delta, int max);

    /** Set a line to an exact quantity (0 removes it). Refreshes TTL. */
    void setQuantity(UUID userId, UUID productId, int quantity);

    /** Remove a line. Refreshes TTL. */
    void remove(UUID userId, UUID productId);

    /** productId -> quantity for the whole cart (empty if none). */
    Map<UUID, Integer> items(UUID userId);

    /** Delete the whole cart. */
    void clear(UUID userId);
}
