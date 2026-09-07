package com.shopflow.order.event;

import com.shopflow.common.event.EventType;
import com.shopflow.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when {@code spring.kafka.bootstrap-servers} is not set (local dev, non-Saga tests). */
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderEventPublisher.class);

    @Override
    public void orderCreated(Order order) {
        log(EventType.ORDER_CREATED, order);
    }

    @Override
    public void paymentRequested(Order order) {
        log(EventType.PAYMENT_REQUESTED, order);
    }

    @Override
    public void orderConfirmed(Order order) {
        log(EventType.ORDER_CONFIRMED, order);
    }

    @Override
    public void orderCancelled(Order order, String reason) {
        log(EventType.ORDER_CANCELLED, order);
    }

    private void log(EventType type, Order order) {
        LoggingOrderEventPublisher.log.info("event (log transport) type={} orderId={} status={}",
                type, order.getId(), order.getStatus());
    }
}
