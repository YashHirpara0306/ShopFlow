package com.shopflow.order.domain;

import com.shopflow.common.error.ConflictException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for legal order transitions (see Phase 1 §9 / ADR-0002).
 *
 * <pre>
 *   PENDING            -> INVENTORY_PENDING | CANCELLED
 *   INVENTORY_PENDING  -> AWAITING_PAYMENT  | CANCELLED
 *   AWAITING_PAYMENT   -> CONFIRMED         | CANCELLED
 *   CONFIRMED          -> REFUNDED
 *   CANCELLED / REFUNDED / FAILED           (terminal)
 * </pre>
 *
 * Any state may also move to FAILED (a Saga that could not complete or compensate).
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.INVENTORY_PENDING, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.INVENTORY_PENDING, EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.AWAITING_PAYMENT, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.REFUNDED, OrderStatus.FAILED));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    public static void validate(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new ConflictException("Illegal order transition " + from + " -> " + to);
        }
    }
}
