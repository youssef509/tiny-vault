package com.youssef.storageservice.security;

import com.youssef.storageservice.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for extracting the authenticated User from the SecurityContext.
 *
 * Usage in controllers:
 *   User currentUser = SecurityUtils.getCurrentUser();
 *
 * This is safe to call in any method downstream of the ApiKeyAuthFilter,
 * because the filter guarantees a valid ApiKeyAuthenticationToken is set
 * before the request reaches any controller.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the currently authenticated User.
     *
     * @throws IllegalStateException if called outside an authenticated request context
     */
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthenticationToken token) {
            return token.getPrincipal();
        }
        throw new IllegalStateException(
            "No authenticated user in security context — " +
            "this method must only be called within an authenticated request");
    }
}
