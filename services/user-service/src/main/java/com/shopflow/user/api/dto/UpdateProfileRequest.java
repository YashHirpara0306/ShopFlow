package com.shopflow.user.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial profile update. {@code null} fields are left unchanged; {@code displayName} cannot be blanked.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String displayName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,20}$", message = "must be a valid phone number") String phone
) {
}
