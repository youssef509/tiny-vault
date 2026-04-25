package com.youssef.storageservice.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token bucket rate limiter — enforced per API key.
 *
 * Algorithm: Token Bucket (via Bucket4j)
 *   - Each API key gets its own bucket of N tokens
 *   - Tokens refill at N per minute (greedy — spread evenly across the window)
 *   - Consuming one token per request
 *   - When bucket is empty → 429 Too Many Requests
 *
 * Why in-memory? This is a personal self-hosted service with a single instance.
 * For multi-instance deployments, swap ConcurrentHashMap for Bucket4j + Redis.
 *
 * Response headers added on every authenticated request:
 *   X-RateLimit-Limit:     max requests per minute
 *   X-RateLimit-Remaining: tokens left in the current window
 *
 * On rate limit exceeded (429):
 *   Retry-After: seconds until next token is available
 */
@Slf4j
@Component
@Order(2)   // Runs after AuditLoggingFilter (Order=1), before ApiKeyAuthFilter (Spring Security)
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Per-API-key token buckets. Lazy-initialized on first request. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.requests-per-minute:100}")
    private long requestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(ApiKeyAuthFilter.HEADER_API_KEY);

        // Skip rate limiting for unauthenticated requests (public endpoints, /health)
        // Those are already secured by the auth filter or permitted explicitly
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get or create the bucket for this API key
        Bucket bucket = buckets.computeIfAbsent(apiKey, k -> createBucket());

        // Try to consume one token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Always add rate limit headers so clients can self-throttle
        response.setHeader("X-RateLimit-Limit",     String.valueOf(requestsPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(
                Math.max(0, probe.getRemainingTokens())));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(
                    probe.getNanosToWaitForRefill()) + 1;

            log.warn("Rate limit exceeded for API key [{}] on {} {} — retry in {}s",
                    ApiKeyAuthenticationService.maskKey(apiKey),
                    request.getMethod(), request.getRequestURI(), retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("""
                    {
                      "timestamp": "%s",
                      "status": 429,
                      "error": "Too Many Requests",
                      "message": "Rate limit exceeded. Max %d requests per minute.",
                      "retryAfterSeconds": %d
                    }
                    """.formatted(Instant.now(), requestsPerMinute, retryAfterSeconds));
        }
    }

    /** Skip rate limiting for health/actuator and public file paths. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
            || path.startsWith("/actuator/")
            || path.startsWith("/api/v1/public/")
            || path.equals("/error");
    }

    /**
     * Creates a new token bucket with a greedy refill strategy.
     * Greedy = tokens are refilled smoothly (not all at once at minute boundary).
     */
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
