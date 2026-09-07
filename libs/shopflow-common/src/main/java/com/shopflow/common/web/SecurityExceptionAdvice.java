package com.shopflow.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * Method-security ({@code @PreAuthorize}) failures are thrown inside the DispatcherServlet, so MVC's
 * {@code @RestControllerAdvice} resolution sees them before Spring Security's
 * {@code ExceptionTranslationFilter}. Without this, {@link GlobalExceptionHandler}'s catch-all would
 * turn a 403 into a 500.
 *
 * <p>Ordered ahead of the shared handler; delegates to the same {@link RestAccessDeniedHandler} /
 * {@link RestAuthenticationEntryPoint} used for filter-level denials, so the body is identical
 * however the denial was raised.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityExceptionAdvice(RestAccessDeniedHandler accessDeniedHandler,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(HttpServletRequest request,
                                   HttpServletResponse response,
                                   AccessDeniedException ex) throws IOException {
        accessDeniedHandler.handle(request, response, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public void handleAuthentication(HttpServletRequest request,
                                     HttpServletResponse response,
                                     AuthenticationException ex) throws IOException {
        authenticationEntryPoint.commence(request, response, ex);
    }
}
