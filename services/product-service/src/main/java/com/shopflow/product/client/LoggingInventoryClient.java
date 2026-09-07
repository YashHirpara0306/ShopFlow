package com.shopflow.product.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoggingInventoryClient implements InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingInventoryClient.class);

    @Override
    public void registerNewProduct(UUID productId, String sku) {
        log.info("would call inventory-service to create stock row productId={} sku={} qty=0 (Phase 8 wires HTTP)",
                productId, sku);
    }
}
