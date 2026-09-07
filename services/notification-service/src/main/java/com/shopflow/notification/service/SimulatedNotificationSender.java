package com.shopflow.notification.service;

import com.shopflow.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Logs the "message" and returns success/failure.
 *
 * <ul>
 *   <li>template {@code "FAIL"} always fails - a deterministic hook for tests.</li>
 *   <li>otherwise fails with probability {@code shopflow.notification.fail-rate}.</li>
 * </ul>
 */
@Component
public class SimulatedNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    private final double failRate;
    private final SecureRandom random = new SecureRandom();

    public SimulatedNotificationSender(@Value("${shopflow.notification.fail-rate:0.0}") double failRate) {
        this.failRate = Math.max(0.0, Math.min(1.0, failRate));
    }

    @Override
    public String send(Channel channel, String template, UUID userId, String data) {
        if ("FAIL".equalsIgnoreCase(template)) {
            return "simulated permanent failure for template FAIL";
        }
        if (random.nextDouble() < failRate) {
            return "simulated transient provider error";
        }
        log.info("[{}] sent template={} to user={} data={}", channel, template, userId, data);
        return null;
    }
}
