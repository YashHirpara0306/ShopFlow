package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * Credentials were supplied but are wrong - bad email/password on login, or a refresh token that is
 * unknown, expired, or revoked. Maps to HTTP 401.
 *
 * <p>The message is deliberately vague ("Invalid credentials") so it does not reveal whether the
 * email exists.
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, message);
    }
}
