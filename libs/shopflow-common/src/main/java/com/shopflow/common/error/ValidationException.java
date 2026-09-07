package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * A domain-level validation failure that Bean Validation annotations could not express
 * (e.g. "requested quantity exceeds the per-order limit"). Maps to HTTP 400.
 *
 * <p>For simple field constraints prefer {@code @NotNull} / {@code @Size} / {@code @Positive} on the
 * DTO - those are handled automatically and produce field-level messages.
 */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }
}
