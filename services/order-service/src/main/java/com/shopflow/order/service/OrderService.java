package com.shopflow.order.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.IdempotencyConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.common.error.ValidationException;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.order.api.dto.OrderResponse;
import com.shopflow.order.client.CartClient;
import com.shopflow.order.domain.IdempotencyKey;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.domain.OrderStatusHistory;
import com.shopflow.order.event.OrderEventPublisher;
import com.shopflow.order.repository.IdempotencyKeyRepository;
import com.shopflow.order.repository.OrderRepository;
import com.shopflow.order.repository.OrderStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final CartClient cartClient;
    private final OrderEventPublisher events;

    public OrderService(OrderRepository orders,
                        OrderStatusHistoryRepository history,
                        IdempotencyKeyRepository idempotencyKeys,
                        CartClient cartClient,
                        OrderEventPublisher events) {
        this.orders = orders;
        this.history = history;
        this.idempotencyKeys = idempotencyKeys;
        this.cartClient = cartClient;
        this.events = events;
    }

    @Transactional
    public OrderResponse create(UUID userId, String idempotencyKey, String bearerToken) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required");
        }
        String requestHash = sha256(userId + "|createOrder");

        var existing = idempotencyKeys.findByIdemKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return replayOrConflict(existing, requestHash);
        }

        CartClient.CartView cart = cartClient.getCart(bearerToken);
        if (cart.isEmpty()) {
            throw new ValidationException("Cart is empty");
        }

        UUID orderId = UUID.randomUUID();
        try {
            idempotencyKeys.saveAndFlush(new IdempotencyKey(idempotencyKey, userId, requestHash, orderId));
        } catch (DataIntegrityViolationException race) {
            return replayOrConflict(idempotencyKeys.findByIdemKey(idempotencyKey).orElseThrow(), requestHash);
        }

        List<Order.NewLine> lines = cart.items().stream()
                .map(l -> new Order.NewLine(l.productId(), l.name(), l.unitPrice(), l.quantity()))
                .toList();
        BigDecimal total = cart.subtotal() != null ? cart.subtotal()
                : lines.stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.create(orderId, userId, lines, total);
        orders.save(order);
        history.save(order.initialHistory(cid()));
        events.orderCreated(order); // outbox (same tx)
        history.save(order.transitionTo(OrderStatus.INVENTORY_PENDING, "order created, awaiting inventory", cid()));
        cartClient.clear(bearerToken);

        log.info("order created id={} userId={} total={} lines={}", orderId, userId, total, lines.size());
        return OrderMapper.toResponse(order, this.history.findByOrderIdOrderByIdAsc(orderId));
    }

    @Transactional
    public OrderResponse cancel(UUID orderId, UUID callerId, boolean admin) {
        Order order = loadForCaller(orderId, callerId, admin);
        if (!order.getStatus().isCancellable()) {
            throw new ConflictException("An order in status " + order.getStatus() + " can no longer be cancelled");
        }
        history.save(order.transitionTo(OrderStatus.CANCELLED, "CUSTOMER_CANCELLED", cid()));
        events.orderCancelled(order, "CUSTOMER_CANCELLED");
        return detail(order);
    }

    // ---- Saga step handlers (called by OrderSagaListener; guarded so late/duplicate events are no-ops) ----

    @Transactional
    public void onInventoryReserved(UUID orderId) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.INVENTORY_PENDING) {
            log.info("ignoring INVENTORY_RESERVED for order {} in status {}", orderId, order.getStatus());
            return;
        }
        history.save(order.transitionTo(OrderStatus.AWAITING_PAYMENT, "inventory reserved", cid()));
        events.paymentRequested(order);
    }

    @Transactional
    public void onInventoryReservationFailed(UUID orderId, String reason) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.INVENTORY_PENDING) {
            return;
        }
        history.save(order.transitionTo(OrderStatus.CANCELLED, "OUT_OF_STOCK", cid()));
        events.orderCancelled(order, "OUT_OF_STOCK"); // no charge was made; nothing to compensate
        log.info("order {} cancelled: {}", orderId, reason);
    }

    @Transactional
    public void onPaymentSucceeded(UUID orderId) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }
        history.save(order.transitionTo(OrderStatus.CONFIRMED, "payment captured", cid()));
        events.orderConfirmed(order);
    }

    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        Order order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }
        history.save(order.transitionTo(OrderStatus.CANCELLED, "PAYMENT_DECLINED", cid()));
        events.orderCancelled(order, "PAYMENT_DECLINED"); // Inventory releases the reservation
    }

    /** Watchdog: an order stuck pre-confirmation past the timeout is cancelled (compensations follow). */
    @Transactional
    public int cancelStuckOrders(java.time.Duration timeout) {
        var cutoff = java.time.Instant.now().minus(timeout);
        List<Order> stuck = orders.findByStatusInAndCreatedAtBefore(
                List.of(OrderStatus.PENDING, OrderStatus.INVENTORY_PENDING, OrderStatus.AWAITING_PAYMENT), cutoff);
        for (Order order : stuck) {
            history.save(order.transitionTo(OrderStatus.CANCELLED, "TIMEOUT", cid()));
            events.orderCancelled(order, "TIMEOUT");
            log.warn("watchdog cancelled stuck order {} (was {})", order.getId(), order.getStatus());
        }
        return stuck.size();
    }

    private Order requireOrder(UUID orderId) {
        return orders.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }

    /** Admin / internal: drives a status transition. Phase 11 replaces most callers with the Saga. */
    @Transactional
    public OrderResponse updateStatus(UUID orderId, OrderStatus target, String reason) {
        Order order = orders.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        history.save(order.transitionTo(target, reason, cid()));
        if (target == OrderStatus.CONFIRMED) {
            events.orderConfirmed(order);
        } else if (target == OrderStatus.CANCELLED) {
            events.orderCancelled(order, reason);
        }
        return detail(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID orderId, UUID callerId, boolean admin) {
        return detail(loadForCaller(orderId, callerId, admin));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(UUID callerId, boolean admin, OrderStatus status, UUID userIdFilter, Pageable pageable) {
        Page<Order> page;
        if (admin) {
            if (userIdFilter != null && status != null) {
                page = orders.findByUserIdAndStatus(userIdFilter, status, pageable);
            } else if (userIdFilter != null) {
                page = orders.findByUserId(userIdFilter, pageable);
            } else if (status != null) {
                page = orders.findByStatus(status, pageable);
            } else {
                page = orders.findAll(pageable);
            }
        } else {
            page = (status != null)
                    ? orders.findByUserIdAndStatus(callerId, status, pageable)
                    : orders.findByUserId(callerId, pageable);
        }
        return page.map(OrderMapper::toSummary);
    }

    // --- helpers ---

    private Order loadForCaller(UUID orderId, UUID callerId, boolean admin) {
        Order order = orders.findWithItemsById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (!admin && !order.isOwnedBy(callerId)) {
            // do not reveal that the order exists
            throw ResourceNotFoundException.of("Order", orderId);
        }
        return order;
    }

    private OrderResponse replayOrConflict(IdempotencyKey key, String requestHash) {
        if (!key.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key was already used for a different request");
        }
        Order order = orders.findWithItemsById(key.getOrderId())
                .orElseThrow(() -> ResourceNotFoundException.of("Order", key.getOrderId()));
        return detail(order);
    }

    private OrderResponse detail(Order order) {
        return OrderMapper.toResponse(order, history.findByOrderIdOrderByIdAsc(order.getId()));
    }

    private static String cid() {
        return CorrelationId.current();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
