package com.shopflow.notification.service;

import com.shopflow.notification.domain.Channel;

/** Stand-in for an email/SMS provider. Phase 1 non-goal: no real integration. */
public interface NotificationSender {

    /** @return an error string on failure, or {@code null} on success. */
    String send(Channel channel, String template, java.util.UUID userId, String data);
}
