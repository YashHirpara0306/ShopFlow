package com.shopflow.user.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String phone,
        Set<String> roles,
        String status,
        Instant createdAt
) {
}
