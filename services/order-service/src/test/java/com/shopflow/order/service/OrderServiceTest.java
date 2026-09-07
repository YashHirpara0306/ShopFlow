package com.shopflow.order.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.IdempotencyConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.common.error.ValidationException;
import com.shopflow.order.client.CartClient;
import com.shopflow.order.domain.IdempotencyKey;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.event.OrderEventPublisher;
import com.shopflow.order.repository.IdempotencyKeyRepository;
import com.shopflow.order.repository.OrderRepository;
import com.shopflow.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock IdempotencyKeyRepository idempotencyKeys;
    @Mock CartClient cartClient;
    @Mock OrderEventPublisher events;

    OrderService service() {
        return new OrderService(orders, history, idempotencyKeys, cartClient, events);
    }

    private final UUID userId = UUID.randomUUID();

    private CartClient.CartView cartWithOneItem() {
        return new CartClient.CartView(
                List.of(new CartClient.CartView.Line(UUID.randomUUID(), "Widget", new BigDecimal("50.00"), 2)),
                new BigDecimal("100.00"), "INR");
    }

    @Test
    void createRequiresAnIdempotencyKey() {
        assertThatThrownBy(() -> service().create(userId, "  ", "Bearer x"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createRejectsAnEmptyCart() {
        when(idempotencyKeys.findByIdemKey("k1")).thenReturn(Optional.empty());
        when(cartClient.getCart(any())).thenReturn(new CartClient.CartView(List.of(), BigDecimal.ZERO, "INR"));

        assertThatThrownBy(() -> service().create(userId, "k1", "Bearer x"))
                .isInstanceOf(ValidationException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void createSnapshotsTheCartPersistsHistoryPublishesAndClearsTheCart() {
        when(idempotencyKeys.findByIdemKey("k1")).thenReturn(Optional.empty());
        when(cartClient.getCart(any())).thenReturn(cartWithOneItem());
        when(history.findByOrderIdOrderByIdAsc(any())).thenReturn(List.of());

        var response = service().create(userId, "k1", "Bearer tok");

        assertThat(response.status()).isEqualTo("INVENTORY_PENDING"); // created PENDING, then advanced
        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        verify(idempotencyKeys).saveAndFlush(any(IdempotencyKey.class));
        verify(orders).save(any(Order.class));
        verify(history, org.mockito.Mockito.atLeastOnce()).save(any());
        verify(events).orderCreated(any());
        verify(cartClient).clear("Bearer tok");
    }

    @Test
    void createWithAKnownKeyAndSameRequestReplaysTheOriginalOrder() {
        UUID existingOrderId = UUID.randomUUID();
        String hash = OrderService.sha256(userId + "|createOrder");
        IdempotencyKey key = new IdempotencyKey("k1", userId, hash, existingOrderId);
        when(idempotencyKeys.findByIdemKey("k1")).thenReturn(Optional.of(key));
        Order existing = Order.create(existingOrderId, userId,
                List.of(new Order.NewLine(UUID.randomUUID(), "W", new BigDecimal("10.00"), 1)), new BigDecimal("10.00"));
        when(orders.findWithItemsById(existingOrderId)).thenReturn(Optional.of(existing));
        lenient().when(history.findByOrderIdOrderByIdAsc(any())).thenReturn(List.of());

        var response = service().create(userId, "k1", "Bearer x");

        assertThat(response.id()).isEqualTo(existingOrderId);
        verify(orders, never()).save(any());
        verify(cartClient, never()).getCart(any());
    }

    @Test
    void createWithAKnownKeyButDifferentRequestIsAConflict() {
        IdempotencyKey key = new IdempotencyKey("k1", userId, "some-other-hash", UUID.randomUUID());
        when(idempotencyKeys.findByIdemKey("k1")).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> service().create(userId, "k1", "Bearer x"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void cancelRejectsAnOrderThatIsNoLongerCancellable() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, userId,
                List.of(new Order.NewLine(UUID.randomUUID(), "W", new BigDecimal("10.00"), 1)), new BigDecimal("10.00"));
        order.transitionTo(OrderStatus.INVENTORY_PENDING, null, null);
        order.transitionTo(OrderStatus.AWAITING_PAYMENT, null, null);
        order.transitionTo(OrderStatus.CONFIRMED, null, null);
        when(orders.findWithItemsById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().cancel(orderId, userId, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelHidesOrdersOwnedBySomeoneElse() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, UUID.randomUUID(),
                List.of(new Order.NewLine(UUID.randomUUID(), "W", new BigDecimal("10.00"), 1)), new BigDecimal("10.00"));
        when(orders.findWithItemsById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().cancel(orderId, userId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatusRejectsAnIllegalTransition() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, userId,
                List.of(new Order.NewLine(UUID.randomUUID(), "W", new BigDecimal("10.00"), 1)), new BigDecimal("10.00"));
        when(orders.findWithItemsById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().updateStatus(orderId, OrderStatus.CONFIRMED, "nope"))
                .isInstanceOf(ConflictException.class);
    }
}
