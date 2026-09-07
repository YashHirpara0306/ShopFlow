package com.shopflow.cart.service;

import com.shopflow.cart.api.dto.AddItemRequest;
import com.shopflow.cart.api.dto.CartResponse;
import com.shopflow.cart.client.ProductClient;
import com.shopflow.cart.store.CartStore;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String DEFAULT_CURRENCY = "INR";

    private final CartStore store;
    private final ProductClient productClient;
    private final int maxPerLine;

    public CartService(CartStore store,
                       ProductClient productClient,
                       @Value("${shopflow.cart.max-quantity-per-line:99}") int maxPerLine) {
        this.store = store;
        this.productClient = productClient;
        this.maxPerLine = maxPerLine;
    }

    public CartResponse addItem(UUID userId, AddItemRequest request) {
        // fail fast if the product does not exist / is inactive
        productClient.findActive(request.productId())
                .orElseThrow(() -> new ValidationException("Product is not available"));
        int newQty = store.addQuantity(userId, request.productId(), request.quantity(), maxPerLine);
        log.info("cart add userId={} productId={} qty={}", userId, request.productId(), newQty);
        return getCart(userId);
    }

    public CartResponse updateQuantity(UUID userId, UUID productId, int quantity) {
        if (!store.items(userId).containsKey(productId)) {
            throw ResourceNotFoundException.of("Cart item", productId);
        }
        store.setQuantity(userId, productId, quantity);
        return getCart(userId);
    }

    public CartResponse removeItem(UUID userId, UUID productId) {
        store.remove(userId, productId);
        return getCart(userId);
    }

    public void clear(UUID userId) {
        store.clear(userId);
    }

    /** Builds the priced view. Products that have since gone away/inactive are pruned. */
    public CartResponse getCart(UUID userId) {
        Map<UUID, Integer> items = store.items(userId);
        List<CartResponse.CartLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQty = 0;

        for (Map.Entry<UUID, Integer> entry : items.entrySet()) {
            UUID productId = entry.getKey();
            int qty = entry.getValue();
            var snapshot = productClient.findActive(productId).orElse(null);
            if (snapshot == null) {
                store.remove(userId, productId); // prune stale line
                continue;
            }
            BigDecimal lineTotal = snapshot.price().multiply(BigDecimal.valueOf(qty));
            lines.add(new CartResponse.CartLine(productId, snapshot.name(), snapshot.price(), qty, lineTotal));
            subtotal = subtotal.add(lineTotal);
            totalQty += qty;
        }

        return new CartResponse(userId, lines, totalQty, subtotal, DEFAULT_CURRENCY);
    }
}
