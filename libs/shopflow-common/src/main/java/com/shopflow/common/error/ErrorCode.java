package com.shopflow.common.error;

/**
 * Stable, machine-readable error codes returned in the {@code error} field of {@link ErrorResponse}.
 *
 * <p>Clients branch on these constants, never on the human-readable {@code message} (which may be
 * reworded at any time). The set is deliberately small; add a code only when a client genuinely
 * needs to react differently.
 */
public enum ErrorCode {

    /** Request body / params failed Bean Validation or a domain invariant. HTTP 400. */
    VALIDATION_ERROR,

    /** Missing/invalid/expired credentials or token. HTTP 401. */
    AUTHENTICATION_FAILED,

    /** Authenticated but not allowed to perform this action. HTTP 403. */
    ACCESS_DENIED,

    /** The referenced resource does not exist (or is hidden from this caller). HTTP 404. */
    RESOURCE_NOT_FOUND,

    /** The request conflicts with current state (e.g. duplicate SKU, illegal state transition). HTTP 409. */
    CONFLICT,

    /** A retried request carried a known idempotency key but different content. HTTP 409. */
    IDEMPOTENCY_CONFLICT,

    /** The caller exceeded a rate limit. HTTP 429. */
    RATE_LIMITED,

    /** A downstream service or dependency was unavailable / timed out. HTTP 503. */
    DOWNSTREAM_UNAVAILABLE,

    /** Unexpected server-side failure. HTTP 500. Never leaks internals to the client. */
    INTERNAL_ERROR
}
