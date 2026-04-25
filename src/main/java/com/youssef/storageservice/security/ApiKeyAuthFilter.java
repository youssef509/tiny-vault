package com.youssef.storageservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Servlet filter that enforces API key + secret authentication on every request.
 *
 * Runs once per request (extends OncePerRequestFilter).
 * Skips public endpoints (e.g. /health, /actuator/health).
 *
 * Request header requirements:
 *   X-API-Key:    your-api-key
 *   X-API-Secret: your-api-secret
 *
 * On success → sets the authenticated token in SecurityContextHolder.
 * On failure → immediately writes a 401 JSON response and stops the filter chain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY    = "X-API-Key";
    public static final String HEADER_API_SECRET = "X-API-Secret";

    private final ApiKeyAuthenticationService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("ApiKeyAuthFilter processing: {} {}", request.getMethod(), path);

        // Extract headers
        String apiKey    = request.getHeader(HEADER_API_KEY);
        String apiSecret = request.getHeader(HEADER_API_SECRET);

        // Check both headers are present and non-blank
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            log.debug("Missing auth headers on {}", path);
            writeUnauthorized(response, "Missing X-API-Key or X-API-Secret header");
            return;
        }

        try {
            // Validate credentials and get authenticated token
            ApiKeyAuthenticationToken authToken = authService.authenticate(apiKey, apiSecret);

            // Store in SecurityContextHolder so controllers can access the current user
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // Continue down the filter chain — request is authenticated
            filterChain.doFilter(request, response);

        } catch (AuthenticationException ex) {
            // Clear any partial auth state
            SecurityContextHolder.clearContext();
            log.warn("Authentication failed for {} {}: {}", request.getMethod(), path, ex.getMessage());
            writeUnauthorized(response, "Invalid API key or secret");
        }
    }

    /**
     * Returns true for endpoints that don't require authentication.
     * Spring will skip this filter entirely for these paths.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
            || path.startsWith("/actuator/health")
            || path.startsWith("/actuator/info")
            // Public files — no API key needed, served to browsers directly
            || path.startsWith("/api/v1/public/");
    }

    /**
     * Writes a standardized 401 JSON response.
     * Inline JSON avoids a Jackson dependency cycle at this filter stage.
     */
    private void writeUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {
                  "timestamp": "%s",
                  "status": 401,
                  "error": "Unauthorized",
                  "message": "%s"
                }
                """.formatted(Instant.now(), message));
    }
}
