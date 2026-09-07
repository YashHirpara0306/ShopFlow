package com.shopflow.common.security;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** The presented token is missing, malformed, expired, or has a bad signature. Maps to HTTP 401. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, message, cause);
    }
}
