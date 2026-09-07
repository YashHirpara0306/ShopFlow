package com.shopflow.inventory.service;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * At least one line could not be reserved (insufficient stock). The whole reservation is rolled
 * back - inventory is all-or-nothing. Maps to HTTP 409; in the Saga this becomes
 * {@code INVENTORY_RESERVATION_FAILED} and the order is cancelled without charging.
 */
public class ReservationFailedException extends ApiException {

    public ReservationFailedException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
