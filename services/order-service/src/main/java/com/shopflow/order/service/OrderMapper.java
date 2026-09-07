package com.shopflow.order.service;

import com.shopflow.order.api.dto.OrderItemResponse;
import com.shopflow.order.api.dto.OrderResponse;
import com.shopflow.order.api.dto.OrderStatusHistoryResponse;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderStatusHistory;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order, List<OrderStatusHistory> history) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.lineTotal()))
                .toList();
        List<OrderStatusHistoryResponse> historyDtos = history.stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getReason(), h.getCreatedAt()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getUserId(), order.getStatus().name(), order.getCurrency(),
                order.getTotalAmount(), order.getCancelReason(), items, historyDtos,
                order.getCreatedAt(), order.getUpdatedAt());
    }

    public static OrderResponse toSummary(Order order) {
        return toResponse(order, List.of());
    }
}
