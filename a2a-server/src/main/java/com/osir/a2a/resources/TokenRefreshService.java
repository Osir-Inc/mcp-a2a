package com.osir.a2a.resources;

import com.osir.mcp.clients.KeycloakDeviceAuthClient;
import com.osir.mcp.models.auth.AuthTokenResponse;
import com.osir.mcp.services.AuthContext;
import com.osir.mcp.services.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Handles token lifecycle for A2A requests:
 * 1. Validates token claims (expiry, issuer) locally
 * 2. Returns seconds-until-expiry for warning headers
 * 3. Optionally refreshes via KeyCloak when a refresh_token is provided
 */
@ApplicationScoped
public class TokenRefreshService {

    private static final Logger LOG = Logger.getLogger(TokenRefreshService.class);
    private static final long EXPIRY_WARNING_THRESHOLD = 300; // 5 minutes

    @Inject
    @RestClient
    KeycloakDeviceAuthClient keycloakClient;

    @Inject
    AuthService authService;

    @ConfigProperty(name = "keycloak.client-id", defaultValue = "osir-cli")
    String clientId;

    /**
     * Check token expiry. Returns seconds until expiry, or -1 if expired/invalid.
     */
    public long getSecondsUntilExpiry(String bearerToken) {
        String rawToken = stripBearer(bearerToken);
        Map<String, Object> claims = authService.parseJwtClaims(rawToken);
        if (claims == null) return -1;

        Object exp = claims.get("exp");
        if (exp instanceof Number) {
            long expiresAt = ((Number) exp).longValue();
            long remaining = expiresAt - (System.currentTimeMillis() / 1000);
            return remaining;
        }
        return -1;
    }

    /**
     * Returns true if the token is within the warning threshold (5 min).
     */
    public boolean isNearExpiry(String bearerToken) {
        long remaining = getSecondsUntilExpiry(bearerToken);
        return remaining >= 0 && remaining <= EXPIRY_WARNING_THRESHOLD;
    }

    /**
     * Returns true if the token is expired.
     */
    public boolean isExpired(String bearerToken) {
        return getSecondsUntilExpiry(bearerToken) < 0;
    }

    /**
     * Attempt to refresh the token via KeyCloak using the provided refresh token.
     * Returns the new bearer token string, or null if refresh failed.
     */
    public RefreshResult refresh(String refreshToken) {
        try {
            AuthTokenResponse response = keycloakClient.refreshToken(
                    "refresh_token", refreshToken, clientId);

            if (response != null && response.getAccessToken() != null) {
                String newBearer = (response.getTokenType() != null ? response.getTokenType() : "Bearer")
                        + " " + response.getAccessToken();
                LOG.infof("Token refreshed successfully via KeyCloak");
                return new RefreshResult(newBearer, response.getRefreshToken(), response.getExpiresIn());
            }
            return null;
        } catch (Exception e) {
            LOG.warnf("Token refresh via KeyCloak failed: %s", e.getMessage());
            return null;
        }
    }

    private String stripBearer(String token) {
        return token != null && token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    public record RefreshResult(String accessToken, String refreshToken, Long expiresIn) {}
}
