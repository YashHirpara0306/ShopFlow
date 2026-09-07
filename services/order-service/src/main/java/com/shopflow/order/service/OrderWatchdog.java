package com.shopflow.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Cancels orders that got stuck pre-confirmation (e.g. a lost event, a downed consumer). */
@Component
public class OrderWatchdog {

    private final OrderService orderService;
    private final Duration timeout;

    public OrderWatchdog(OrderService orderService,
                         @Value("${shopflow.order.saga-timeout:5m}") Duration timeout) {
        this.orderService = orderService;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${shopflow.order.watchdog-interval:60s}")
    public void run() {
        orderService.cancelStuckOrders(timeout);
    }
}
