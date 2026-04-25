package com.youssef.storageservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration — allows browser-based requests from your other apps
 * (Next.js, Laravel, etc.) to call this API directly.
 *
 * Two scenarios this enables:
 *
 * 1. Server-side upload (recommended):
 *    Your Next.js API route / Laravel controller calls this API with key+secret.
 *    Returns a publicUrl. Frontend uses the publicUrl to display the file.
 *    → No CORS needed for uploads (server-to-server).
 *
 * 2. Public file serving:
 *    GET /api/v1/public/{filename} — no auth, browser can fetch directly.
 *    This is what you use for <img src>, Next.js <Image>, etc.
 *    → Needs CORS for browser access.
 *
 * Configure allowed origins in application.yml:
 *   app:
 *     cors:
 *       allowed-origins: http://localhost:3000,https://myapp.com
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of allowed origins.
     * In dev: allow everything (*).
     * In prod: restrict to your actual frontend domains.
     */
    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders(
                    "X-API-Key",
                    "X-API-Secret",
                    "Content-Type",
                    "Accept",
                    "Authorization"
                )
                .exposedHeaders(
                    "Content-Disposition",
                    "Content-Type",
                    "Content-Length"
                )
                .allowCredentials(false)   // API key auth, not cookie-based
                .maxAge(3600);             // preflight cache: 1 hour
    }
}
