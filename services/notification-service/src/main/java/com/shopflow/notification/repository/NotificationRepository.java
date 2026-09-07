package com.shopflow.notification.repository;

import com.shopflow.notification.domain.Channel;
import com.shopflow.notification.domain.Notification;
import com.shopflow.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByEventIdAndChannel(UUID eventId, Channel channel);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    List<Notification> findTop100ByStatusInOrderByCreatedAtAsc(List<NotificationStatus> statuses);
}
