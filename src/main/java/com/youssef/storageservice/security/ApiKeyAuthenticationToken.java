package com.youssef.storageservice.security;

import com.youssef.storageservice.model.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Custom Spring Security Authentication token for API key-based auth.
 *
 * This token is placed into the SecurityContextHolder after successful
 * authentication. It carries the fully authenticated User as the principal,
 * so any controller can call:
 *   ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
 * to get the current user.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    /** The authenticated user — available to controllers via SecurityContextHolder. */
    private final User principal;

    /**
     * Creates an authenticated token. Always use this constructor after
     * successful credential verification.
     *
     * @param user the verified, active User from the database
     */
    public ApiKeyAuthenticationToken(User user) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.principal = user;
        // Mark as authenticated — Spring Security will allow the request through
        setAuthenticated(true);
    }

    /**
     * Credentials are not stored after authentication to reduce memory exposure.
     * The raw API secret is never kept beyond the auth filter.
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    /** Returns the authenticated User entity. */
    @Override
    public User getPrincipal() {
        return principal;
    }
}
