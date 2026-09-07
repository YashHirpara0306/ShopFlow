package com.shopflow.notification.service;

import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.notification.api.dto.NotificationResponse;
import com.shopflow.notification.domain.Channel;
import com.shopflow.notification.domain.Notification;
import com.shopflow.notification.domain.NotificationAttempt;
import com.shopflow.notification.domain.NotificationStatus;
import com.shopflow.notification.repository.NotificationAttemptRepository;
import com.shopflow.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationAttemptRepository attempts;
    private final NotificationSender sender;
    private final int maxAttempts;

    public NotificationService(NotificationRepository notifications,
                               NotificationAttemptRepository attempts,
                               NotificationSender sender,
                               @Value("${shopflow.notification.max-attempts:5}") int maxAttempts) {
        this.notifications = notifications;
        this.attempts = attempts;
        this.sender = sender;
        this.maxAttempts = maxAttempts;
    }

    /** Idempotent per {@code (eventId, channel)} - a redelivered event does not resend. */
    @Transactional
    public NotificationResponse request(UUID eventId, UUID userId, Channel channel, String template, String data) {
        Notification existing = notifications.findByEventIdAndChannel(eventId, channel).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        Notification notification = Notification.pending(eventId, userId, channel, template, data);
        try {
            notifications.saveAndFlush(notification);
        } catch (DataIntegrityViolationException race) {
            return toResponse(notifications.findByEventIdAndChannel(eventId, channel).orElseThrow());
        }
        attemptSend(notification);
        return toResponse(notification);
    }

    @Transactional
    public NotificationResponse retry(UUID id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (notification.getStatus() == NotificationStatus.SENT) {
            return toResponse(notification);
        }
        attemptSend(notification);
        return toResponse(notification);
    }

    /** Scheduled sweep: re-attempt anything not SENT and under the attempt cap. */
    @Transactional
    public int retryPending() {
        List<Notification> due = notifications.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(NotificationStatus.PENDING, NotificationStatus.FAILED));
        int retried = 0;
        for (Notification n : due) {
            if (n.isRetryable(maxAttempts)) {
                attemptSend(n);
                retried++;
            }
        }
        if (retried > 0) {
            log.info("retry sweep re-attempted {} notification(s)", retried);
        }
        return retried;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(UUID userId, Pageable pageable) {
        return notifications.findByUserId(userId, pageable).map(NotificationService::toResponse);
    }

    private void attemptSend(Notification notification) {
        String error = sender.send(notification.getChannel(), notification.getTemplate(),
                notification.getUserId(), notification.getData());
        if (error == null) {
            notification.recordSuccess();
            attempts.save(new NotificationAttempt(notification.getId(), notification.getAttempts(), "SENT", null));
        } else {
            notification.recordFailure(error);
            attempts.save(new NotificationAttempt(notification.getId(), notification.getAttempts(), "FAILED", error));
            log.warn("notification send failed id={} attempt={} error={}",
                    notification.getId(), notification.getAttempts(), error);
        }
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventId(), n.getUserId(), n.getChannel().name(),
                n.getTemplate(), n.getStatus().name(), n.getAttempts(), n.getLastError(),
                n.getCreatedAt(), n.getSentAt());
    }
}
