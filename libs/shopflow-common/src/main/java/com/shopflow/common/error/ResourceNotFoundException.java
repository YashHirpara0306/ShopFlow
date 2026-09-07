package com.shopflow.common.error;

import org.springframework.http.HttpStatus;

/** The requested resource does not exist, or is not visible to this caller. Maps to HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /** Convenience: {@code new ResourceNotFoundException("Product", productId)} -> "Product 42 not found". */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " " + id + " not found");
    }
}
