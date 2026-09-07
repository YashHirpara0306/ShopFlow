package com.shopflow.order.event;

import com.shopflow.order.domain.Order;

/**
 * Seam for publishing order domain events. Phase 7 logs them; Phase 11 replaces the implementation
 * with a Kafka producer to {@code order-events} (partition key = orderId) and wires the real Saga.
 */
public interface OrderEventPublisher {

    void orderCreated(Order order);

    void orderConfirmed(Order order);

    void orderCancelled(Order order, String reason);
}
