package com.shopflow.common.error;

import java.time.Instant;

/**
 * The single error body shape returned by every ShopFlow service (and the gateway) on any 4xx/5xx.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "timestamp": "2026-01-01T10:00:00Z",
 *   "status": 400,
 *   "error": "VALIDATION_ERROR",
 *   "message": "email must be a well-formed address",
 *   "path": "/api/users/register",
 *   "correlationId": "a1f9c2d4-..."
 * }
 * }</pre>
 *
 * <p>The {@code correlationId} lets a support engineer paste one id into a log query and see the
 * whole request across services. The response never contains a stack trace or internal detail.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {

    public static ErrorResponse of(int status,
                                   ErrorCode error,
                                   String message,
                                   String path,
                                   String correlationId) {
        return new ErrorResponse(Instant.now(), status, error.name(), message, path, correlationId);
    }
}
