package com.shopflow.product.client;

import com.shopflow.common.error.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** Calls Inventory Service {@code POST /api/inventory/stock} to create a stock row (quantity 0). */
@Component
public class HttpInventoryClient implements InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInventoryClient.class);

    private final RestClient restClient;

    public HttpInventoryClient(RestClient.Builder builder,
                               @Value("${shopflow.clients.inventory-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void registerNewProduct(UUID productId, String sku, String bearerToken) {
        try {
            restClient.post()
                    .uri("/api/inventory/stock")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("productId", productId.toString(), "quantity", 0))
                    .retrieve()
                    .toBodilessEntity();
            log.info("registered stock row productId={} sku={}", productId, sku);
        } catch (Exception e) {
            // Product creation already committed; a missing stock row is fixable by an admin.
            // Surfacing 503 lets the caller retry the whole create if they wish.
            throw new DownstreamUnavailableException("Inventory Service could not register the product", e);
        }
    }
}
