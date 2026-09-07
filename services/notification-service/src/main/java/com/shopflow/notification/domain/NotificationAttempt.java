package com.shopflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_attempt")
public class NotificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(nullable = false, length = 10)
    private String outcome;

    @Column(length = 300)
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationAttempt() {
    }

    public NotificationAttempt(UUID notificationId, int attemptNo, String outcome, String error) {
        this.notificationId = notificationId;
        this.attemptNo = attemptNo;
        this.outcome = outcome;
        this.error = error;
    }
}
