package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.clients.KeycloakDeviceAuthClient;
import com.osir.mcp.models.AuthResult;
import com.osir.mcp.models.AuthStatusResult;
import com.osir.mcp.models.auth.*;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-connection authentication state for MCP sessions.
 * Tokens are scoped to a connection ID and automatically refreshed before expiry.
 * Note: connection IDs change on reconnect — users must re-authenticate after a
 * network disconnection.
 */
@ApplicationScoped
public class SessionAwareAuthService {

    private static final Logger LOG = Logger.getLogger(SessionAwareAuthService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    @RestClient
    KeycloakDeviceAuthClient keycloakClient;

    @ConfigProperty(name = "keycloak.client-id", defaultValue = "osir-cli")
    String clientId;

    @ConfigProperty(name = "auth.token.refresh-buffer-seconds", defaultValue = "60")
    long refreshBufferSeconds;

    // connection ID -> SessionAuth
    private final Map<String, SessionAuth> sessionAuths = new ConcurrentHashMap<>();
    // deviceCode -> DeviceFlowEntry (connectionId + expiry) — for ownership validation
    private final Map<String, DeviceFlowEntry> pendingDeviceFlows = new ConcurrentHashMap<>();

    public enum AuthCheck { AUTHENTICATED, NOT_AUTHENTICATED, EXPIRED }

    public AuthCheck checkAuth(String connectionId) {
        if (!sessionAuths.containsKey(connectionId)) return AuthCheck.NOT_AUTHENTICATED;
        return getCurrentToken(connectionId) != null ? AuthCheck.AUTHENTICATED : AuthCheck.EXPIRED;
    }

    public AuthStatusResult getAuthStatus(String connectionId) {
        SessionAuth sessionAuth = sessionAuths.get(connectionId);

        if (sessionAuth == null) {
            LOG.debugf("No authentication found for connection %s", connectionId);
            return new AuthStatusResult(false, null, null);
        }

        // Refresh if needed (mirrors getCurrentToken) rather than discarding the session — a status
        // check during a long operation (e.g. a multi-minute build) must NOT throw away a still-valid
        // refresh token, or the user gets logged out mid-deploy.
        if (getCurrentToken(connectionId) == null) {
            LOG.debugf("Authentication expired and not refreshable for connection %s", connectionId);
            return new AuthStatusResult(false, null, null);
        }
        SessionAuth current = sessionAuths.get(connectionId);   // may have been refreshed above

        long expiresIn = (current.getExpiresAt() - System.currentTimeMillis()) / 1000;
        LOG.debugf("Connection %s authenticated as %s, expires in %d seconds",
                connectionId, current.getUsername(), expiresIn);

        return new AuthStatusResult(true, current.getUsername(), expiresIn);
    }

    public AuthResult logout(String connectionId) {
        SessionAuth removed = sessionAuths.remove(connectionId);
        if (removed != null) {
            try {
                keycloakClient.revokeToken(removed.getAccessToken(), "access_token", clientId);
            } catch (Exception e) {
                LOG.warnf("Failed to revoke access token for connection %s: %s", connectionId, e.getMessage());
            }
            if (removed.getRefreshToken() != null) {
                try {
                    keycloakClient.backchannelLogout(removed.getRefreshToken(), clientId);
                } catch (Exception e) {
                    LOG.warnf("Failed to revoke refresh token for connection %s: %s", connectionId, e.getMessage());
                }
            }
            LOG.infof("User %s logged out from connection %s, tokens revoked at Keycloak", removed.getUsername(), connectionId);
        }
        return new AuthResult(true, "Logged out successfully");
    }

    public String getCurrentToken(String connectionId) {
        SessionAuth sessionAuth = sessionAuths.get(connectionId);

        if (sessionAuth == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        long expiresAt = sessionAuth.getExpiresAt();

        // Token expired — attempt refresh before giving up
        if (now > expiresAt) {
            if (sessionAuth.getRefreshToken() != null) {
                LOG.debugf("Token expired for connection %s, attempting refresh", connectionId);
                return refreshSession(connectionId, sessionAuth);
            }
            sessionAuths.remove(connectionId);
            return null;
        }

        // Proactive refresh when within the buffer window
        long secondsUntilExpiry = (expiresAt - now) / 1000;
        if (secondsUntilExpiry <= refreshBufferSeconds && sessionAuth.getRefreshToken() != null) {
            LOG.debugf("Token for connection %s expires in %ds (within %ds buffer), proactively refreshing",
                    connectionId, secondsUntilExpiry, refreshBufferSeconds);
            String refreshed = refreshSession(connectionId, sessionAuth);
            if (refreshed != null) return refreshed;
            // Refresh failed but token still valid — fall through and return it
        }

        return sessionAuth.getTokenType() + " " + sessionAuth.getAccessToken();
    }

    public boolean isAuthenticated(String connectionId) {
        return getCurrentToken(connectionId) != null;
    }

    public DeviceLoginResult startDeviceLogin(String connectionId) {
        try {
            DeviceCodeResponse response = keycloakClient.requestDeviceCode(clientId, "openid");
            if (response == null) {
                return new DeviceLoginResult(false, "Failed to initiate device login: no response from KeyCloak");
            }
            long expiresAt = System.currentTimeMillis() + (response.getExpiresIn() * 1000L);
            pendingDeviceFlows.put(response.getDeviceCode(), new DeviceFlowEntry(connectionId, expiresAt));
            LOG.infof("Device login initiated for connection %s. User code: %s", connectionId, response.getUserCode());
            return new DeviceLoginResult(
                    true,
                    "Please visit the verification URL and enter the user code to authenticate.",
                    response.getDeviceCode(),
                    response.getUserCode(),
                    response.getVerificationUri(),
                    response.getVerificationUriComplete(),
                    response.getExpiresIn(),
                    response.getInterval()
            );
        } catch (Exception e) {
            LOG.errorf(e, "Device login initiation failed for connection %s: %s", connectionId, e.getMessage());
            return new DeviceLoginResult(false, "Device login failed: " + e.getMessage());
        }
    }

    public DeviceLoginStatusResult checkDeviceLoginStatus(String connectionId, String deviceCode) {
        DeviceFlowEntry entry = pendingDeviceFlows.get(deviceCode);
        if (entry == null) {
            return new DeviceLoginStatusResult(false, "Unknown device code. Please start a new login.", "invalid");
        }
        // Ownership check: reject if this device code was issued to a different connection
        if (!connectionId.equals(entry.connectionId())) {
            LOG.warnf("Connection %s attempted to poll device code issued for connection %s",
                    connectionId, entry.connectionId());
            return new DeviceLoginStatusResult(false, "Unknown device code. Please start a new login.", "invalid");
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            pendingDeviceFlows.remove(deviceCode);
            return new DeviceLoginStatusResult(false, "Device code has expired. Please start a new login.", "expired");
        }

        try {
            AuthTokenResponse tokenResponse = keycloakClient.pollDeviceToken(
                    "urn:ietf:params:oauth:grant-type:device_code", clientId, deviceCode);
            pendingDeviceFlows.remove(deviceCode);

            String username = extractUsername(tokenResponse.getAccessToken());
            SessionAuth sessionAuth = new SessionAuth(
                    username,
                    tokenResponse.getAccessToken(),
                    tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                    System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null
                            ? tokenResponse.getExpiresIn() * 1000L : 3600000L),
                    tokenResponse.getRefreshToken()
            );
            sessionAuths.put(connectionId, sessionAuth);
            LOG.infof("Device login complete for user %s on connection %s", username, connectionId);
            return new DeviceLoginStatusResult(
                    true,
                    "Authentication successful via device flow.",
                    "complete",
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getTokenType()
            );
        } catch (WebApplicationException e) {
            String errorCode = parseOAuthError(e);
            return switch (errorCode) {
                case "authorization_pending" ->
                        new DeviceLoginStatusResult(true,
                                "Waiting for user to authorize. Please complete login in your browser.", "pending");
                case "slow_down" ->
                        new DeviceLoginStatusResult(true,
                                "Polling too fast. Please wait a few seconds before retrying.", "slow_down");
                case "expired_token" -> {
                    pendingDeviceFlows.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false,
                            "Device code has expired. Please start a new login.", "expired");
                }
                case "access_denied" -> {
                    pendingDeviceFlows.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false,
                            "Authorization was denied by the user.", "denied");
                }
                default -> {
                    LOG.warnf("Unexpected OAuth error during device poll: %s", errorCode);
                    yield new DeviceLoginStatusResult(false,
                            "Device login check failed: " + errorCode, errorCode);
                }
            };
        } catch (Exception e) {
            LOG.errorf(e, "Device login status check failed: %s", e.getMessage());
            return new DeviceLoginStatusResult(false, "Device login check failed: " + e.getMessage(), "error");
        }
    }

    private String refreshSession(String connectionId, SessionAuth current) {
        try {
            AuthTokenResponse tokenResponse = keycloakClient.refreshToken(
                    "refresh_token", current.getRefreshToken(), clientId);
            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                SessionAuth refreshed = new SessionAuth(
                        current.getUsername(),
                        tokenResponse.getAccessToken(),
                        tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                        System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null
                                ? tokenResponse.getExpiresIn() * 1000L : 3600000L),
                        tokenResponse.getRefreshToken() != null
                                ? tokenResponse.getRefreshToken() : current.getRefreshToken()
                );
                sessionAuths.put(connectionId, refreshed);
                LOG.infof("Token refreshed for user %s on connection %s", current.getUsername(), connectionId);
                return refreshed.getTokenType() + " " + refreshed.getAccessToken();
            }
        } catch (Exception e) {
            LOG.warnf("Token refresh failed for connection %s: %s", connectionId, e.getMessage());
        }
        sessionAuths.remove(connectionId);
        return null;
    }

    @Scheduled(every = "1h")
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int sessionsBefore = sessionAuths.size();
        int flowsBefore = pendingDeviceFlows.size();
        sessionAuths.entrySet().removeIf(entry -> entry.getValue().isExpired());
        pendingDeviceFlows.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
        LOG.debugf("Cleanup: removed %d expired sessions, %d abandoned device flows",
                sessionsBefore - sessionAuths.size(), flowsBefore - pendingDeviceFlows.size());
    }

    private String extractUsername(String accessToken) {
        try {
            if (accessToken == null || accessToken.isBlank()) return "unknown";
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return "unknown";
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = MAPPER.readValue(payload, Map.class);
            Object username = claims.get("preferred_username");
            return username != null ? username.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String parseOAuthError(WebApplicationException e) {
        try {
            String body = e.getResponse().readEntity(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(body, Map.class);
            Object err = map.get("error");
            return err != null ? err.toString() : "unknown_error";
        } catch (Exception parseEx) {
            return "unknown_error";
        }
    }

    private record DeviceFlowEntry(String connectionId, long expiresAt) {}

    private static class SessionAuth {
        private final String username;
        private final String accessToken;
        private final String tokenType;
        private final long expiresAt;
        private final String refreshToken;

        SessionAuth(String username, String accessToken, String tokenType,
                    long expiresAt, String refreshToken) {
            this.username = username;
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresAt = expiresAt;
            this.refreshToken = refreshToken;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }

        String getUsername() { return username; }
        String getAccessToken() { return accessToken; }
        String getTokenType() { return tokenType; }
        long getExpiresAt() { return expiresAt; }
        String getRefreshToken() { return refreshToken; }
    }
}
