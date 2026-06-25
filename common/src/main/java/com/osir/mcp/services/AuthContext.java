package com.osir.mcp.services;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped auth context that allows per-request token override.
 * Used by A2A agents to propagate user tokens without mutating the global AuthService session.
 *
 * When set, AuthService.getCurrentToken() returns this token instead of the session token.
 * When not set (null), AuthService falls back to its normal session-based token.
 *
 * Token refresh is handled by TokenRefreshService in the A2A module.
 * If a token is refreshed, the new token is set here via setTokenOverride() and
 * returned to the client in the response metadata.
 */
@RequestScoped
public class AuthContext {

    private String tokenOverride;

    public String getTokenOverride() {
        return tokenOverride;
    }

    public void setTokenOverride(String token) {
        this.tokenOverride = token;
    }

    public boolean hasOverride() {
        return tokenOverride != null && !tokenOverride.isBlank();
    }
}
