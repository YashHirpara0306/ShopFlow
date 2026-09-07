package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * A request reused a known {@code Idempotency-Key} but with a different body than the original.
 * The safe response is to reject it rather than guess which one the caller meant. Maps to HTTP 409.
 *
 * <p>Note: a <em>replay</em> of the same key with the <em>same</em> body is not an error - the
 * service returns the original result. This exception is only for a genuine key clash.
 */
public class IdempotencyConflictException extends ApiException {

    public IdempotencyConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT, message);
    }
}
