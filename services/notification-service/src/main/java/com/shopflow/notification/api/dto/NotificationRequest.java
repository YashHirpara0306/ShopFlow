package com.shopflow.notification.api.dto;

import com.shopflow.notification.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record NotificationRequest(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @NotNull Channel channel,
        @NotBlank @Size(max = 60) String template,
        @Size(max = 2000) String data
) {
}
