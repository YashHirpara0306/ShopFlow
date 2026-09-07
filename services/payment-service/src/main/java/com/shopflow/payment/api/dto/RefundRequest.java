package com.shopflow.payment.api.dto;

import jakarta.validation.constraints.Size;

public record RefundRequest(
        @Size(max = 200) String reason
) {
}
