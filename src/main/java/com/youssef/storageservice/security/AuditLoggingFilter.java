package com.youssef.storageservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Audit logging filter — logs every API request with timing and masked API key.
 *
 * Runs at the very beginning of the filter chain (Order=1) so it captures
 * the total request processing time including auth and controller logic.
 *
 * Log format:
 *   → POST /api/v1/upload [key=yous***-v1]
 *   ← POST /api/v1/upload 201 in 145ms [key=yous***-v1]
 *
 * Skips internal Spring dispatch paths (/error, /favicon.ico).
 */
@Slf4j
@Component
@Order(1)
public class AuditLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String path   = request.getRequestURI();
        String maskedKey = maskApiKey(request.getHeader(ApiKeyAuthFilter.HEADER_API_KEY));

        log.info("→ {} {} [key={}]", method, path, maskedKey);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int  status   = response.getStatus();

            // Use INFO for success, WARN for client errors, ERROR for server errors
            if (status >= 500) {
                log.error("← {} {} {} in {}ms [key={}]", method, path, status, duration, maskedKey);
            } else if (status >= 400) {
                log.warn("← {} {} {} in {}ms [key={}]", method, path, status, duration, maskedKey);
            } else {
                log.info("← {} {} {} in {}ms [key={}]", method, path, status, duration, maskedKey);
            }
        }
    }

    /** Skip logging for Spring's internal error/favicon paths to reduce noise. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/error") || path.equals("/favicon.ico");
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "anonymous";
        return ApiKeyAuthenticationService.maskKey(apiKey);
    }
}
