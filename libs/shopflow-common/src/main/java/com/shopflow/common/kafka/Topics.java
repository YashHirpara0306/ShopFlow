package com.shopflow.common.kafka;

/**
 * The four business topics and their dead-letter companions. Four topics, not one-per-event-type
 * (see Phase 1 §6). Partition key is {@code orderId} for order/inventory/payment, {@code userId}
 * for notification.
 */
public final class Topics {

    private Topics() {
    }

    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";

    public static final int PARTITIONS = 3;

    public static String dlt(String topic) {
        return topic + ".DLT";
    }
}
