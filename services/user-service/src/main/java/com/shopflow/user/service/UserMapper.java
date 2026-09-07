package com.shopflow.user.service;

import com.shopflow.user.api.dto.UserResponse;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;

import java.util.stream.Collectors;

/** Entity -> DTO. Entities never leave the service layer. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRoles().stream().map(Role::name).collect(Collectors.toUnmodifiableSet()),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
