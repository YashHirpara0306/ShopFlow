package com.shopflow.payment.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulatorTest {

    @Test
    void amountEndingInThirteenAlwaysDeclines() {
        PaymentSimulator simulator = new PaymentSimulator(0.0); // "never fail" rate
        var outcome = simulator.charge(new BigDecimal("120.13"));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("DECLINED");
    }

    @RepeatedTest(20)
    void failRateZeroMeansAlwaysSucceed() {
        var outcome = new PaymentSimulator(0.0).charge(new BigDecimal("99.00"));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.providerRef()).startsWith("sim_");
    }

    @RepeatedTest(20)
    void failRateOneMeansAlwaysFail() {
        var outcome = new PaymentSimulator(1.0).charge(new BigDecimal("99.00"));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("SIMULATED_ERROR");
    }
}
