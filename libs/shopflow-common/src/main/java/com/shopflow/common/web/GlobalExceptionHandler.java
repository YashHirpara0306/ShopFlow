package com.shopflow.common.web;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.ErrorCode;
import com.shopflow.common.error.ErrorResponse;
import com.shopflow.common.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Turns every exception that escapes a controller into the one {@link ErrorResponse} body shape,
 * with the correct HTTP status and the request's correlation id. Registered automatically for
 * servlet services by {@code WebErrorHandlingAutoConfiguration}.
 *
 * <p>Design:
 * <ul>
 *   <li>Expected, client-facing failures are {@link ApiException} subclasses - one handler covers all.</li>
 *   <li>Framework validation exceptions are mapped to {@code 400 VALIDATION_ERROR} with a readable message.</li>
 *   <li>Anything else is an unexpected {@code 500}: logged at ERROR with the correlation id and a stack
 *       trace, but the client only ever sees a generic message - no internals leak.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        // 4xx are the caller's problem - log at WARN without a stack trace. 5xx keep the cause.
        if (ex.getStatus().is5xxServerError()) {
            log.error("ApiException [{}] on {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("ApiException [{}] on {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                detail.isEmpty() ? "Request validation failed" : detail, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex,
                                                               HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                detail.isEmpty() ? "Constraint violation" : detail, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Malformed or missing request data", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code,
                                                       String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), code, message, request.getRequestURI(), CorrelationId.current());
        return ResponseEntity.status(status).body(body);
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + " " + (fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage());
    }
}
