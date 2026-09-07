package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with the current state of the resource: a duplicate unique value
 * (e.g. SKU already exists) or an illegal state transition (e.g. cancelling a shipped order).
 * Maps to HTTP 409.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
