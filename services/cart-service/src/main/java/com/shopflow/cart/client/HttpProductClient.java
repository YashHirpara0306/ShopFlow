package com.shopflow.cart.client;

import com.shopflow.common.error.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Calls Product Service {@code GET /api/products/{id}} (public, cache-backed). */
@Component
public class HttpProductClient implements ProductClient {

    private static final Logger log = LoggerFactory.getLogger(HttpProductClient.class);

    private final RestClient restClient;

    public HttpProductClient(RestClient.Builder builder,
                             @Value("${shopflow.clients.product-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<ProductSnapshot> findActive(UUID productId) {
        try {
            ProductDto dto = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductDto.class);
            if (dto == null || !dto.active()) {
                return Optional.empty();
            }
            return Optional.of(new ProductSnapshot(dto.id(), dto.name(), dto.price(), dto.currency(), dto.active()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("product-service returned {} for {}", e.getStatusCode(), productId);
            throw new DownstreamUnavailableException("Product lookup failed", e);
        } catch (Exception e) {
            throw new DownstreamUnavailableException("Product Service is unavailable", e);
        }
    }

    private record ProductDto(UUID id, String name, BigDecimal price, String currency, boolean active) {
    }
}
