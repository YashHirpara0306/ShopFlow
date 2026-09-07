package com.shopflow.order.domain;

public enum OrderStatus {
    PENDING,
    INVENTORY_PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    REFUNDED,
    FAILED;

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED || this == FAILED;
    }

    public boolean isCancellable() {
        return this == PENDING || this == INVENTORY_PENDING || this == AWAITING_PAYMENT;
    }
}
