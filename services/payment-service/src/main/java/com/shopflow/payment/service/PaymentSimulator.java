package com.shopflow.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;

/**
 * Stand-in for a payment gateway (no real integration - ADR / Phase 1 non-goal).
 *
 * <ul>
 *   <li>An amount whose paise/cents part is {@code 13} <b>always</b> declines - a deterministic hook
 *       for tests and demos.</li>
 *   <li>Otherwise it declines with probability {@code shopflow.payment.fail-rate}.</li>
 * </ul>
 */
@Component
public class PaymentSimulator {

    private final double failRate;
    private final SecureRandom random = new SecureRandom();

    public PaymentSimulator(@Value("${shopflow.payment.fail-rate:0.15}") double failRate) {
        this.failRate = Math.max(0.0, Math.min(1.0, failRate));
    }

    public record Outcome(boolean success, String reason, String providerRef) {
    }

    public Outcome charge(BigDecimal amount) {
        int fraction = amount.setScale(2, RoundingMode.HALF_UP).remainder(BigDecimal.ONE)
                .movePointRight(2).intValueExact();
        if (fraction == 13) {
            return new Outcome(false, "DECLINED", null);
        }
        if (random.nextDouble() < failRate) {
            return new Outcome(false, "SIMULATED_ERROR", null);
        }
        return new Outcome(true, null, "sim_" + Long.toHexString(random.nextLong()));
    }
}
