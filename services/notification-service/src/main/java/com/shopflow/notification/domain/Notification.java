package com.shopflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 60)
    private String template;

    @Column(length = 2000)
    private String data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
    }

    public static Notification pending(UUID eventId, UUID userId, Channel channel, String template, String data) {
        Notification n = new Notification();
        n.id = UUID.randomUUID();
        n.eventId = eventId;
        n.userId = userId;
        n.channel = channel;
        n.template = template;
        n.data = data;
        n.status = NotificationStatus.PENDING;
        n.attempts = 0;
        return n;
    }

    public void recordSuccess() {
        this.attempts++;
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.status = NotificationStatus.FAILED;
        this.lastError = error != null && error.length() > 300 ? error.substring(0, 300) : error;
    }

    public boolean isRetryable(int maxAttempts) {
        return status != NotificationStatus.SENT && attempts < maxAttempts;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getTemplate() {
        return template;
    }

    public String getData() {
        return data;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
