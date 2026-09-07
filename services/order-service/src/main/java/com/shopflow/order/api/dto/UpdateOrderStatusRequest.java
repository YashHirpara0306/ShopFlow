package com.shopflow.order.api.dto;

import com.shopflow.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 200) String reason
) {
}
