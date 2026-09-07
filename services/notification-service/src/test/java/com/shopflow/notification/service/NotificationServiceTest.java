package com.shopflow.notification.service;

import com.shopflow.notification.domain.Channel;
import com.shopflow.notification.domain.Notification;
import com.shopflow.notification.domain.NotificationStatus;
import com.shopflow.notification.repository.NotificationAttemptRepository;
import com.shopflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock NotificationAttemptRepository attempts;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    NotificationService service(NotificationSender sender) {
        return new NotificationService(notifications, attempts, sender, 5);
    }

    private final NotificationSender alwaysOk = (c, t, u, d) -> null;
    private final NotificationSender alwaysFail = (c, t, u, d) -> "boom";

    @Test
    void requestSendsAndMarksSent() {
        when(notifications.findByEventIdAndChannel(eventId, Channel.EMAIL)).thenReturn(Optional.empty());

        var response = service(alwaysOk).request(eventId, userId, Channel.EMAIL, "ORDER_CONFIRMED", "{}");

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.attempts()).isEqualTo(1);
        verify(attempts).save(any());
    }

    @Test
    void requestRecordsAFailureAndAnAttemptRow() {
        when(notifications.findByEventIdAndChannel(eventId, Channel.EMAIL)).thenReturn(Optional.empty());

        var response = service(alwaysFail).request(eventId, userId, Channel.EMAIL, "ORDER_CONFIRMED", "{}");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.lastError()).isEqualTo("boom");
        verify(attempts).save(any());
    }

    @Test
    void requestIsIdempotentPerEventAndChannel() {
        Notification existing = Notification.pending(eventId, userId, Channel.EMAIL, "T", "{}");
        existing.recordSuccess();
        when(notifications.findByEventIdAndChannel(eventId, Channel.EMAIL)).thenReturn(Optional.of(existing));

        var response = service(alwaysFail).request(eventId, userId, Channel.EMAIL, "T", "{}");

        assertThat(response.status()).isEqualTo("SENT");
        verify(notifications, never()).saveAndFlush(any());
    }

    @Test
    void retrySucceedsAfterAnEarlierFailure() {
        Notification failed = Notification.pending(eventId, userId, Channel.EMAIL, "T", "{}");
        failed.recordFailure("first error");
        when(notifications.findById(failed.getId())).thenReturn(Optional.of(failed));

        var response = service(alwaysOk).retry(failed.getId());

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.attempts()).isEqualTo(2);
    }

    @Test
    void retrySweepReattemptsOnlyRetryableNotifications() {
        Notification retryable = Notification.pending(eventId, userId, Channel.EMAIL, "T", "{}");
        retryable.recordFailure("e");
        Notification exhausted = Notification.pending(UUID.randomUUID(), userId, Channel.SMS, "T", "{}");
        for (int i = 0; i < 5; i++) {
            exhausted.recordFailure("e");
        }
        when(notifications.findTop100ByStatusInOrderByCreatedAtAsc(any()))
                .thenReturn(java.util.List.of(retryable, exhausted));

        int retried = service(alwaysOk).retryPending();

        assertThat(retried).isEqualTo(1);
        assertThat(retryable.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(exhausted.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
