package com.shopflow.order.event;

import com.shopflow.common.event.EventType;
import com.shopflow.common.kafka.Topics;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderItem;
import com.shopflow.order.kafka.OutboxWriter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes every order event to the transactional outbox in the same transaction as the state change
 * (ADR-0005 #2). {@link OutboxWriter} enqueues; {@code OutboxRelay} publishes to Kafka.
 */
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final OutboxWriter outbox;

    public KafkaOrderEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void orderCreated(Order order) {
        Map<String, Object> payload = base(order);
        payload.put("createdAt", Instant.now().toString());
        payload.put("currency", order.getCurrency());
        payload.put("totalAmount", order.getTotalAmount().toPlainString());
        payload.put("lines", order.getItems().stream().map(KafkaOrderEventPublisher::line).toList());
        outbox.enqueue(Topics.ORDER_EVENTS, key(order), EventType.ORDER_CREATED, 1, payload);
    }

    @Override
    public void paymentRequested(Order order) {
        Map<String, Object> payload = base(order);
        payload.put("amount", order.getTotalAmount().toPlainString());
        payload.put("currency", order.getCurrency());
        payload.put("requestedAt", Instant.now().toString());
        outbox.enqueue(Topics.PAYMENT_EVENTS, key(order), EventType.PAYMENT_REQUESTED, 1, payload);
    }

    @Override
    public void orderConfirmed(Order order) {
        Map<String, Object> payload = base(order);
        payload.put("confirmedAt", Instant.now().toString());
        payload.put("totalAmount", order.getTotalAmount().toPlainString());
        outbox.enqueue(Topics.ORDER_EVENTS, key(order), EventType.ORDER_CONFIRMED, 1, payload);
    }

    @Override
    public void orderCancelled(Order order, String reason) {
        Map<String, Object> payload = base(order);
        payload.put("cancelledAt", Instant.now().toString());
        payload.put("reason", reason);
        outbox.enqueue(Topics.ORDER_EVENTS, key(order), EventType.ORDER_CANCELLED, 1, payload);
    }

    private static Map<String, Object> base(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", order.getId().toString());
        map.put("userId", order.getUserId().toString());
        return map;
    }

    private static Map<String, Object> line(OrderItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("productId", item.getProductId().toString());
        map.put("name", item.getProductName());
        map.put("unitPrice", item.getUnitPrice().toPlainString());
        map.put("quantity", item.getQuantity());
        return map;
    }

    private static String key(Order order) {
        return order.getId().toString();
    }
}
