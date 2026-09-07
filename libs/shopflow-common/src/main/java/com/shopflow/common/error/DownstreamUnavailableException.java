package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * A synchronous call to another service or dependency failed or timed out, and this request
 * cannot proceed without it (e.g. Order Service could not reach Cart Service to validate the cart).
 * Maps to HTTP 503 so the client knows a retry may succeed.
 */
public class DownstreamUnavailableException extends ApiException {

    public DownstreamUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DOWNSTREAM_UNAVAILABLE, message);
    }

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DOWNSTREAM_UNAVAILABLE, message, cause);
    }
}
