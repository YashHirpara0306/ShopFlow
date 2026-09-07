package com.shopflow.payment.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.payment.api.dto.PaymentResponse;
import com.shopflow.payment.api.dto.RefundResponse;
import com.shopflow.payment.domain.Payment;
import com.shopflow.payment.domain.Refund;
import com.shopflow.payment.repository.PaymentRepository;
import com.shopflow.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentSimulator simulator;

    public PaymentService(PaymentRepository payments, RefundRepository refunds, PaymentSimulator simulator) {
        this.payments = payments;
        this.refunds = refunds;
        this.simulator = simulator;
    }

    /** Idempotent per {@code orderId} ({@code payment.order_id} UNIQUE). A duplicate returns the existing outcome. */
    @Transactional
    public PaymentResponse process(UUID orderId, BigDecimal amount, String currency) {
        Payment existing = payments.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Payment payment = Payment.initiated(orderId, amount, currency);
        try {
            payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException race) {
            return toResponse(payments.findByOrderId(orderId).orElseThrow());
        }

        PaymentSimulator.Outcome outcome = simulator.charge(amount);
        if (outcome.success()) {
            payment.capture(outcome.providerRef());
            log.info("payment captured orderId={} paymentId={} amount={}", orderId, payment.getId(), amount);
        } else {
            payment.fail(outcome.reason());
            log.info("payment failed orderId={} paymentId={} reason={}", orderId, payment.getId(), outcome.reason());
        }
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return payments.findByOrderId(orderId).map(PaymentService::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
    }

    /** Idempotent per {@code orderId}. Only a captured payment can be refunded. */
    @Transactional
    public RefundResponse refund(UUID orderId, String reason) {
        Refund existing = refunds.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment for order", orderId));
        if (!payment.isCaptured()) {
            throw new ConflictException("Order " + orderId + " has no captured payment to refund");
        }

        Refund refund = Refund.completed(payment.getId(), orderId, payment.getAmount(), reason);
        try {
            refunds.saveAndFlush(refund);
        } catch (DataIntegrityViolationException race) {
            return toResponse(refunds.findByOrderId(orderId).orElseThrow());
        }
        log.info("refund completed orderId={} amount={}", orderId, payment.getAmount());
        return toResponse(refund);
    }

    private static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(),
                p.getStatus().name(), p.getFailureReason(), p.getCreatedAt());
    }

    private static RefundResponse toResponse(Refund r) {
        return new RefundResponse(r.getId(), r.getOrderId(), r.getAmount(), r.getStatus(), r.getCreatedAt());
    }
}
