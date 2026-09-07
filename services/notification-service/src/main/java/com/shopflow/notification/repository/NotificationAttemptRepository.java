package com.shopflow.notification.repository;

import com.shopflow.notification.domain.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {
}
