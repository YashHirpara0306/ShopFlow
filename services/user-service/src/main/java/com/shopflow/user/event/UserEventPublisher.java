package com.shopflow.user.event;

import com.shopflow.user.domain.AppUser;

/**
 * Seam for publishing User Service domain events. Phase 4 has a logging implementation; Phase 11
 * replaces it with a Kafka producer writing {@code USER_REGISTERED} to {@code notification-events}.
 * Nothing else in the service changes.
 */
public interface UserEventPublisher {

    void userRegistered(AppUser user);
}
