package com.shopflow.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password 8-72 chars. The upper bound is BCrypt's limit - longer input is silently
 *                 truncated by BCrypt, so we reject it rather than hash something the user did not type.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName
) {
}
