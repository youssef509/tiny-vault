package com.youssef.storageservice.config;

import com.youssef.storageservice.security.ApiKeyAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Full security configuration for the Storage Service.
 *
 * Security model:
 *  - Stateless (no sessions, no cookies)
 *  - All endpoints require API key + secret EXCEPT /health and /actuator/health
 *  - Custom ApiKeyAuthFilter runs before Spring's default auth filters
 *  - BCrypt strength 12 for hashing API secrets
 *  - CSRF disabled (not applicable for a REST API)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;

    /**
     * Main security filter chain.
     *
     * Public paths (no auth required):
     *   GET /health
     *   GET /actuator/health
     *   GET /actuator/info
     *
     * Protected paths (API key + secret required):
     *   POST   /api/v1/upload
     *   GET    /api/v1/download/{filename}
     *   GET    /api/v1/files
     *   DELETE /api/v1/files/{filename}
     *   GET    /actuator/metrics  (secured by the filter — not public)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // No CSRF needed — pure stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Disable Spring's default form login / basic auth / logout
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            // Stateless session — no HttpSession created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no auth header required
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Spring's internal error dispatch — must be accessible without re-auth
                .requestMatchers("/error").permitAll()

                // Everything else requires authentication (enforced by our filter)
                .anyRequest().authenticated()
            )

            // Plug in our custom API key filter BEFORE Spring's default username/password filter
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
