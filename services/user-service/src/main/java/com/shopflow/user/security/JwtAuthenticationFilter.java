package com.shopflow.user.security;

import com.shopflow.common.security.InvalidTokenException;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>}, validates it, and puts the resulting authentication in
 * the {@link SecurityContextHolder}. Also stamps {@code userId} into the MDC so every log line for
 * an authenticated request carries it.
 *
 * <p>A missing or invalid token is NOT rejected here - the request proceeds unauthenticated and
 * Spring Security's entry point handles it if the target endpoint requires auth. This keeps public
 * endpoints reachable with a stale token in the header.
 *
 * <p>Phase 14 will promote this filter into {@code shopflow-common} so every service shares it.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_MDC_KEY = "userId";

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean mdcSet = false;

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                JwtPrincipal principal = jwtValidator.validate(header.substring(BEARER_PREFIX.length()));
                List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal.userId(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put(USER_ID_MDC_KEY, principal.userId());
                mdcSet = true;
            } catch (InvalidTokenException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (mdcSet) {
                MDC.remove(USER_ID_MDC_KEY);
            }
        }
    }
}
