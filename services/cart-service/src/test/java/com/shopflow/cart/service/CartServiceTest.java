package com.shopflow.cart.service;

import com.shopflow.cart.api.dto.AddItemRequest;
import com.shopflow.cart.api.dto.CartResponse;
import com.shopflow.cart.client.ProductClient;
import com.shopflow.cart.client.ProductClient.ProductSnapshot;
import com.shopflow.cart.store.CartStore;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.common.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartStore store;
    @Mock ProductClient productClient;

    CartService service() {
        return new CartService(store, productClient, 99);
    }

    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private ProductSnapshot snapshot(BigDecimal price) {
        return new ProductSnapshot(productId, "Widget", price, "INR", true);
    }

    @Test
    void addItemValidatesTheProductBeforeWriting() {
        when(productClient.findActive(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addItem(userId, new AddItemRequest(productId, 2)))
                .isInstanceOf(ValidationException.class);
        verify(store, never()).addQuantity(any(), any(), anyInt(), anyInt());
    }

    @Test
    void addItemIncrementsAndReturnsThePricedCart() {
        when(productClient.findActive(productId)).thenReturn(Optional.of(snapshot(new BigDecimal("50.00"))));
        when(store.addQuantity(userId, productId, 2, 99)).thenReturn(2);
        Map<UUID, Integer> items = new LinkedHashMap<>();
        items.put(productId, 2);
        when(store.items(userId)).thenReturn(items);

        CartResponse cart = service().addItem(userId, new AddItemRequest(productId, 2));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.totalQuantity()).isEqualTo(2);
        assertThat(cart.subtotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void updateQuantityFailsWhenTheLineIsNotInTheCart() {
        when(store.items(userId)).thenReturn(Map.of());

        assertThatThrownBy(() -> service().updateQuantity(userId, productId, 3))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(store, never()).setQuantity(any(), any(), anyInt());
    }

    @Test
    void updateQuantitySetsTheExactValue() {
        Map<UUID, Integer> items = new LinkedHashMap<>();
        items.put(productId, 1);
        when(store.items(userId)).thenReturn(items);
        when(productClient.findActive(productId)).thenReturn(Optional.of(snapshot(new BigDecimal("10.00"))));

        service().updateQuantity(userId, productId, 5);

        verify(store).setQuantity(userId, productId, 5);
    }

    @Test
    void getCartPrunesLinesWhoseProductHasGoneAway() {
        UUID gone = UUID.randomUUID();
        Map<UUID, Integer> items = new LinkedHashMap<>();
        items.put(productId, 2);
        items.put(gone, 1);
        when(store.items(userId)).thenReturn(items);
        when(productClient.findActive(productId)).thenReturn(Optional.of(snapshot(new BigDecimal("10.00"))));
        when(productClient.findActive(gone)).thenReturn(Optional.empty());

        CartResponse cart = service().getCart(userId);

        assertThat(cart.items()).hasSize(1);
        verify(store).remove(userId, gone);
    }

    @Test
    void clearDelegatesToTheStore() {
        service().clear(userId);
        verify(store).clear(userId);
    }
}
