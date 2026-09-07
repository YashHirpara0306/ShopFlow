package com.shopflow.notification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-attempts notifications that have not been SENT and are under the attempt cap. */
@Component
public class NotificationRetryJob {

    private final NotificationService notificationService;

    public NotificationRetryJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${shopflow.notification.retry-scan-interval:60s}")
    public void sweep() {
        notificationService.retryPending();
    }
}
