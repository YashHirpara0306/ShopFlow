package com.shopflow.product.client;

import java.util.UUID;

/**
 * When an admin creates a product, Inventory Service must get a stock row (at 0). Per ADR-0005 this
 * is a synchronous REST call, not a Kafka event. The admin's bearer token is forwarded.
 */
public interface InventoryClient {

    void registerNewProduct(UUID productId, String sku, String bearerToken);
}
