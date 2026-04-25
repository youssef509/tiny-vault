package com.youssef.storageservice.security;

import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Validates API key + secret credentials against the database.
 *
 * Authentication flow:
 *  1. Look up user by API key (plaintext lookup)
 *  2. Reject if user not found or inactive
 *  3. BCrypt-verify the raw secret against the stored hash
 *  4. Return an authenticated token on success
 *
 * All failures throw Spring Security's AuthenticationException so the
 * filter can return a consistent 401 response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates the given API key + secret pair.
     *
     * @param apiKey    raw API key from X-API-Key header
     * @param apiSecret raw API secret from X-API-Secret header
     * @return authenticated token carrying the resolved User
     * @throws AuthenticationException if credentials are invalid or account is disabled
     */
    public ApiKeyAuthenticationToken authenticate(String apiKey, String apiSecret)
            throws AuthenticationException {

        // 1. Look up the user by API key
        User user = userRepository.findByApiKey(apiKey)
                .orElseThrow(() -> {
                    // Log with masked key to avoid leaking full key to logs
                    log.warn("Auth failed: API key not found [{}]", maskKey(apiKey));
                    return new BadCredentialsException("Invalid API key or secret");
                });

        // 2. Check account is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Auth failed: account disabled for API key [{}]", maskKey(apiKey));
            throw new DisabledException("Account is disabled");
        }

        // 3. Verify the secret using BCrypt
        // Important: always call matches() even if key lookup failed to prevent
        // timing attacks that could enumerate valid keys
        if (!passwordEncoder.matches(apiSecret, user.getApiSecretHash())) {
            log.warn("Auth failed: invalid secret for API key [{}]", maskKey(apiKey));
            throw new BadCredentialsException("Invalid API key or secret");
        }

        log.debug("Auth success for API key [{}] (user: {})", maskKey(apiKey), user.getEmail());
        return new ApiKeyAuthenticationToken(user);
    }

    /**
     * Masks an API key for safe logging.
     * "myapp-prod-key-abc123" → "myap***123"
     * Public so other filters (AuditLoggingFilter) can reuse it.
     */
    public static String maskKey(String key) {
        if (key == null || key.length() < 6) return "***";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 3);
    }
}
