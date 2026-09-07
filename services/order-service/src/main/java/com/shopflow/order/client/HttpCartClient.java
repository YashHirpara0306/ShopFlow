package com.shopflow.order.client;

import com.shopflow.common.error.DownstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCartClient implements CartClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCartClient.class);

    private final RestClient restClient;

    public HttpCartClient(RestClient.Builder builder,
                          @Value("${shopflow.clients.cart-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public CartView getCart(String bearerToken) {
        try {
            CartView view = restClient.get()
                    .uri("/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(CartView.class);
            return view == null ? new CartView(java.util.List.of(), java.math.BigDecimal.ZERO, "INR") : view;
        } catch (Exception e) {
            throw new DownstreamUnavailableException("Cart Service is unavailable", e);
        }
    }

    @Override
    public void clear(String bearerToken) {
        // Best-effort: the order is already placed. A stale cart is a minor UX issue, not a failure.
        try {
            restClient.delete()
                    .uri("/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("could not clear cart after order creation: {}", e.toString());
        }
    }
}
