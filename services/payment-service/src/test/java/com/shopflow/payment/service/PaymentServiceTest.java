package com.shopflow.payment.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.payment.domain.Payment;
import com.shopflow.payment.repository.PaymentRepository;
import com.shopflow.payment.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock RefundRepository refunds;

    private final UUID orderId = UUID.randomUUID();

    PaymentService service(double failRate) {
        return new PaymentService(payments, refunds, new PaymentSimulator(failRate));
    }

    @Test
    void processCapturesWhenTheSimulatorSucceeds() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        var response = service(0.0).process(orderId, new BigDecimal("100.00"), "INR");
        assertThat(response.status()).isEqualTo("CAPTURED");
    }

    @Test
    void processFailsWhenTheSimulatorDeclines() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        var response = service(1.0).process(orderId, new BigDecimal("100.00"), "INR");
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("SIMULATED_ERROR");
    }

    @Test
    void processIsIdempotentPerOrder() {
        Payment captured = Payment.initiated(orderId, new BigDecimal("50.00"), "INR");
        captured.capture("sim_x");
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(captured));

        var response = service(1.0).process(orderId, new BigDecimal("50.00"), "INR");

        assertThat(response.status()).isEqualTo("CAPTURED");
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void refundRequiresACapturedPayment() {
        Payment failed = Payment.initiated(orderId, new BigDecimal("50.00"), "INR");
        failed.fail("DECLINED");
        when(refunds.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service(0.0).refund(orderId, "customer request"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refundOnAnUnknownOrderIs404() {
        when(refunds.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(0.0).refund(orderId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refundIsIdempotentPerOrder() {
        var refund = com.shopflow.payment.domain.Refund.completed(UUID.randomUUID(), orderId, new BigDecimal("50.00"), "r");
        when(refunds.findByOrderId(orderId)).thenReturn(Optional.of(refund));

        var response = service(0.0).refund(orderId, "again");

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(refunds, never()).saveAndFlush(any());
    }
}
