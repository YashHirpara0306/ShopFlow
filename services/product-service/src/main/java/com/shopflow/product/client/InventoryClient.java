package com.shopflow.product.client;

import java.util.UUID;

/**
 * When an admin creates a product, Inventory Service must get a stock row (at 0). Per ADR-0005 this
 * is a synchronous REST call, not a Kafka event. Phase 5 ships a logging stub; Phase 8 wires the
 * real HTTP client once Inventory Service exists.
 */
public interface InventoryClient {

    void registerNewProduct(UUID productId, String sku);
}
