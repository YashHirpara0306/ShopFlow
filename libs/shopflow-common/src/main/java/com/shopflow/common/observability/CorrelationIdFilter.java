package com.shopflow.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that binds a correlation id for the lifetime of every request.
 *
 * <ol>
 *   <li>Read {@code X-Correlation-Id} from the request; if missing/invalid, generate one.</li>
 *   <li>Put it in the MDC so every log line for this request carries it.</li>
 *   <li>Echo it on the response so the caller (and the gateway) can log the same id.</li>
 *   <li>Always clear the MDC afterwards so a pooled thread never leaks an id into the next request.</li>
 * </ol>
 *
 * <p>Runs first ({@link Ordered#HIGHEST_PRECEDENCE}) so even auth failures are logged with an id.
 * The reactive API Gateway uses its own {@code WebFilter} equivalent (added in Phase 13).
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationId.sanitizeOrCreate(request.getHeader(CorrelationId.HEADER));
        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }
}
