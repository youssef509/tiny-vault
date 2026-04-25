package com.youssef.storageservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the PasswordEncoder bean in its own configuration class.
 *
 * This is intentionally separate from SecurityConfig to break the
 * circular dependency that would otherwise occur:
 *   SecurityConfig → ApiKeyAuthFilter → ApiKeyAuthenticationService
 *                                              → PasswordEncoder
 *                                              → SecurityConfig  ← cycle!
 *
 * By moving PasswordEncoder here, Spring can resolve it independently
 * before wiring either SecurityConfig or ApiKeyAuthenticationService.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt strength 12 ≈ 250ms per hash — good balance of security
     * vs. performance for a personal service with low auth volume.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
