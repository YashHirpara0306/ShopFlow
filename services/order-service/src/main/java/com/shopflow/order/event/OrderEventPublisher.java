package com.shopflow.order.event;

import com.shopflow.order.domain.Order;

/**
 * The events the Order Service emits. Phase 11 impl writes them to the transactional outbox
 * ({@code KafkaOrderEventPublisher}); the {@code Logging} impl is used when Kafka is not configured.
 */
public interface OrderEventPublisher {

    /** order-events, key = orderId. Kicks off the Saga (Inventory reserves). */
    void orderCreated(Order order);

    /** payment-events, key = orderId. Command-style: tells Payment to charge. */
    void paymentRequested(Order order);

    /** order-events, key = orderId. Inventory confirms the reservation; Notification informs the user. */
    void orderConfirmed(Order order);

    /** order-events, key = orderId. Inventory releases the reservation (compensation); Notification informs. */
    void orderCancelled(Order order, String reason);
}
