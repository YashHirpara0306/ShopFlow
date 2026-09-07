package com.shopflow.payment.api;

import com.shopflow.payment.api.dto.PaymentResponse;
import com.shopflow.payment.api.dto.ProcessPaymentRequest;
import com.shopflow.payment.api.dto.RefundRequest;
import com.shopflow.payment.api.dto.RefundResponse;
import com.shopflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Admin / internal. Simulated - no real gateway. From Phase 11 the Saga drives this over Kafka.")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Process a payment for an order (idempotent per orderId)")
    public PaymentResponse process(@Valid @RequestBody ProcessPaymentRequest request) {
        return paymentService.process(request.orderId(), request.amount(), request.currency());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get the payment status for an order")
    public PaymentResponse get(@PathVariable UUID orderId) {
        return paymentService.getByOrderId(orderId);
    }

    @PostMapping("/{orderId}/refund")
    @Operation(summary = "Refund an order's captured payment (idempotent per orderId)")
    public RefundResponse refund(@PathVariable UUID orderId, @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(orderId, request.reason());
    }
}
