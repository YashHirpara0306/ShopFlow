package com.shopflow.user.event;

import com.shopflow.common.event.EventType;
import com.shopflow.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when {@code spring.kafka.bootstrap-servers} is not set. */
public class LoggingUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingUserEventPublisher.class);

    @Override
    public void userRegistered(AppUser user) {
        log.info("event (log transport) type={} userId={}", EventType.USER_REGISTERED, user.getId());
    }
}
