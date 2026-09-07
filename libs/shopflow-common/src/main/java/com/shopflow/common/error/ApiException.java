package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for every expected, client-facing failure in a ShopFlow service.
 *
 * <p>Carrying both the {@link HttpStatus} and the machine-readable {@link ErrorCode} on the
 * exception lets a single handler ({@code GlobalExceptionHandler}) turn any {@code ApiException}
 * into the standard {@link ErrorResponse} body without a big {@code instanceof} ladder.
 *
 * <p>Throw a subclass for anything the caller did wrong or can react to. Let genuinely unexpected
 * failures propagate as-is: the handler maps those to a generic 500 and logs them.
 */
public abstract class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient HttpStatus status;

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ApiException(HttpStatus status, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
