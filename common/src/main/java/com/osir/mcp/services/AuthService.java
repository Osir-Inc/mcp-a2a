package com.osir.mcp.services;

import com.osir.mcp.models.AuthResult;
import com.osir.mcp.models.AuthStatusResult;
import com.osir.mcp.models.auth.*;
import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.clients.KeycloakDeviceAuthClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    @RestClient
    DomainBackendClient backendClient;

    @Inject
    @RestClient
    KeycloakDeviceAuthClient keycloakClient;

    @ConfigProperty(name = "quarkus.rest-client.domain-backend.url", defaultValue = "NOT_CONFIGURED")
    String backendUrl;

    @ConfigProperty(name = "keycloak.client-id", defaultValue = "osir-cli")
    String clientId;

    @ConfigProperty(name = "auth.token.refresh-buffer-seconds", defaultValue = "60")
    long refreshBufferSeconds;

    @Inject
    jakarta.enterprise.inject.Instance<AuthContext> authContextInstance;

    // In-memory session storage
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private volatile String currentSessionId;

    // Track pending device flows: deviceCode -> expiresAtMillis
    private final Map<String, Long> pendingDeviceFlows = new ConcurrentHashMap<>();

    public AuthResult authenticate(String username, String password) {
        try {
            AuthRequest authRequest = new AuthRequest(username, password);
            AuthTokenResponse tokenResponse = backendClient.authenticate(authRequest);

            if (tokenResponse != null) {
                if (tokenResponse.getAccessToken() != null && !tokenResponse.getAccessToken().trim().isEmpty()) {
                    String sessionId = generateSessionId();
                    SessionInfo session = new SessionInfo(
                            username,
                            tokenResponse.getAccessToken(),
                            tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                            System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() * 1000 : 3600000),
                            tokenResponse.getRefreshToken(),
                            "password"
                    );
                    sessions.put(sessionId, session);
                    currentSessionId = sessionId;
                    AuthResult result = new AuthResult(true, "Authentication successful");
                    result.setTokenType(session.getTokenType());
                    result.setExpiresIn(tokenResponse.getExpiresIn());
                    return result;
                } else {
                    return new AuthResult(false, "Authentication failed: No access token received");
                }
            } else {
                return new AuthResult(false, "Authentication failed: No response from backend");
            }

        } catch (Exception e) {
            LOG.errorf(e, "Authentication failed with exception: %s", e.getMessage());
            return new AuthResult(false, "Authentication failed: " + e.getMessage());
        } finally {
            LOG.infof("=== AUTHENTICATION COMPLETE ===");
        }
    }

    public DeviceLoginResult startDeviceLogin() {
        try {
            DeviceCodeResponse response = keycloakClient.requestDeviceCode(clientId, "openid");

            if (response == null) {
                return new DeviceLoginResult(false, "Failed to initiate device login: No response from KeyCloak");
            }

            // Track the pending flow with its expiry time
            pendingDeviceFlows.put(response.getDeviceCode(),
                    System.currentTimeMillis() + (response.getExpiresIn() * 1000L));

            LOG.infof("Device login initiated. User code: %s, Verification URI: %s",
                    response.getUserCode(), response.getVerificationUri());

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
            LOG.errorf(e, "Device login initiation failed: %s", e.getMessage());
            return new DeviceLoginResult(false, "Device login failed: " + e.getMessage());
        }
    }

    public DeviceLoginStatusResult checkDeviceLoginStatus(String deviceCode) {
        // Check if we know about this device code
        Long expiresAt = pendingDeviceFlows.get(deviceCode);
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            pendingDeviceFlows.remove(deviceCode);
            return new DeviceLoginStatusResult(false, "Device code has expired. Please start a new login.", "expired");
        }

        try {
            AuthTokenResponse tokenResponse = keycloakClient.pollDeviceToken(
                    "urn:ietf:params:oauth:grant-type:device_code", clientId, deviceCode);

            // Success - we got a token
            pendingDeviceFlows.remove(deviceCode);

            String username = extractUsernameFromToken(tokenResponse.getAccessToken());

            String sessionId = generateSessionId();
            SessionInfo session = new SessionInfo(
                    username,
                    tokenResponse.getAccessToken(),
                    tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                    System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() * 1000 : 3600000),
                    tokenResponse.getRefreshToken(),
                    "device"
            );
            sessions.put(sessionId, session);
            currentSessionId = sessionId;

            LOG.infof("Device login complete for user: %s", username);

            return new DeviceLoginStatusResult(
                    true,
                    "Authentication successful via device flow.",
                    "complete",
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getTokenType()
            );

        } catch (WebApplicationException e) {
            // Parse the OAuth2 error response
            String errorCode = parseOAuthError(e);

            return switch (errorCode) {
                case "authorization_pending" ->
                        new DeviceLoginStatusResult(true, "Waiting for user to authorize. Please complete login in your browser.", "pending");
                case "slow_down" ->
                        new DeviceLoginStatusResult(true, "Polling too fast. Please increase the interval between checks.", "slow_down");
                case "expired_token" -> {
                    pendingDeviceFlows.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false, "Device code has expired. Please start a new login.", "expired");
                }
                case "access_denied" -> {
                    pendingDeviceFlows.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false, "Authorization was denied by the user.", "denied");
                }
                default -> {
                    LOG.warnf("Unexpected OAuth error during device poll: %s", errorCode);
                    yield new DeviceLoginStatusResult(false, "Device login check failed: " + errorCode, errorCode);
                }
            };
        } catch (Exception e) {
            LOG.errorf(e, "Device login status check failed: %s", e.getMessage());
            return new DeviceLoginStatusResult(false, "Device login check failed: " + e.getMessage(), "error");
        }
    }

    public AuthResult refreshSession() {
        if (currentSessionId == null) {
            return new AuthResult(false, "No active session to refresh");
        }

        SessionInfo session = sessions.get(currentSessionId);
        if (session == null) {
            currentSessionId = null;
            return new AuthResult(false, "No active session to refresh");
        }

        if (session.getRefreshToken() == null) {
            return new AuthResult(false, "No refresh token available");
        }

        try {
            AuthTokenResponse tokenResponse = keycloakClient.refreshToken(
                    "refresh_token", session.getRefreshToken(), clientId);

            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                SessionInfo newSession = new SessionInfo(
                        session.getUsername(),
                        tokenResponse.getAccessToken(),
                        tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                        System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() * 1000 : 3600000),
                        tokenResponse.getRefreshToken() != null ? tokenResponse.getRefreshToken() : session.getRefreshToken(),
                        session.getLoginMethod()
                );
                sessions.put(currentSessionId, newSession);

                LOG.infof("Session refreshed for user: %s", session.getUsername());

                AuthResult result = new AuthResult(true, "Session refreshed successfully");
                result.setTokenType(newSession.getTokenType());
                result.setExpiresIn(tokenResponse.getExpiresIn());
                return result;
            } else {
                return new AuthResult(false, "Token refresh failed: No access token received");
            }
        } catch (Exception e) {
            LOG.errorf(e, "Token refresh failed: %s", e.getMessage());
            return new AuthResult(false, "Token refresh failed: " + e.getMessage());
        }
    }

    public AuthStatusResult getAuthStatus() {
        // Check request-scoped override first (A2A)
        if (authContextInstance != null && authContextInstance.isResolvable()) {
            AuthContext ctx = authContextInstance.get();
            if (ctx.hasOverride()) {
                String token = ctx.getTokenOverride();
                String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
                String username = extractUsernameFromToken(rawToken);
                return new AuthStatusResult(true, username, null);
            }
        }

        if (currentSessionId == null) {
            return new AuthStatusResult(false, null, null);
        }

        SessionInfo session = sessions.get(currentSessionId);
        if (session == null || session.isExpired()) {
            currentSessionId = null;
            return new AuthStatusResult(false, null, null);
        }

        long expiresIn = (session.getExpiresAt() - System.currentTimeMillis()) / 1000;
        return new AuthStatusResult(true, session.getUsername(), expiresIn);
    }

    public AuthResult logout() {
        // A2A context: clear the override
        if (authContextInstance != null && authContextInstance.isResolvable()) {
            AuthContext ctx = authContextInstance.get();
            if (ctx.hasOverride()) {
                ctx.setTokenOverride(null);
                return new AuthResult(true, "Logged out successfully");
            }
        }

        if (currentSessionId != null) {
            sessions.remove(currentSessionId);
            currentSessionId = null;
            LOG.infof("User logged out successfully");
        }
        return new AuthResult(true, "Logged out successfully");
    }

    public String getCurrentToken() {
        // Check for request-scoped token override (used by A2A agents)
        if (authContextInstance != null && authContextInstance.isResolvable()) {
            AuthContext ctx = authContextInstance.get();
            if (ctx.hasOverride()) {
                String override = ctx.getTokenOverride();
                // Validate token claims (expiry, issuer) locally
                String rawToken = override.startsWith("Bearer ") ? override.substring(7) : override;
                if (!validateTokenClaims(rawToken)) {
                    LOG.warnf("A2A token failed local validation (expired or invalid issuer)");
                    return null;
                }
                return override;
            }
        }

        if (currentSessionId == null) {
            return null;
        }

        SessionInfo session = sessions.get(currentSessionId);
        if (session == null) {
            currentSessionId = null;
            return null;
        }

        long now = System.currentTimeMillis();
        long expiresAt = session.getExpiresAt();

        // Token is already expired - try refresh
        if (now > expiresAt) {
            if (session.getRefreshToken() != null) {
                LOG.debugf("Token expired, attempting refresh");
                AuthResult refreshResult = refreshSession();
                if (refreshResult.isSuccess()) {
                    SessionInfo refreshed = sessions.get(currentSessionId);
                    return refreshed.getTokenType() + " " + refreshed.getAccessToken();
                }
            }
            currentSessionId = null;
            return null;
        }

        // Token is close to expiry - proactive refresh
        long secondsUntilExpiry = (expiresAt - now) / 1000;
        if (secondsUntilExpiry <= refreshBufferSeconds && session.getRefreshToken() != null) {
            LOG.debugf("Token expires in %d seconds (within buffer of %d), proactively refreshing",
                    secondsUntilExpiry, refreshBufferSeconds);
            AuthResult refreshResult = refreshSession();
            if (refreshResult.isSuccess()) {
                SessionInfo refreshed = sessions.get(currentSessionId);
                return refreshed.getTokenType() + " " + refreshed.getAccessToken();
            }
            // If proactive refresh fails, still return the current valid token
        }

        return session.getTokenType() + " " + session.getAccessToken();
    }

    public boolean isAuthenticated() {
        return getCurrentToken() != null;
    }

    /**
     * Parse JWT claims without signature verification.
     * Returns null if the token is malformed.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJwtClaims(String accessToken) {
        try {
            if (accessToken == null || accessToken.isBlank()) return null;
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            LOG.warnf("Failed to parse JWT: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Extract username from JWT payload for display/logging only.
     * NOTE: Signature is validated by the backend on every API call.
     */
    String extractUsernameFromToken(String accessToken) {
        Map<String, Object> claims = parseJwtClaims(accessToken);
        if (claims == null) return "unknown";
        Object username = claims.get("preferred_username");
        return username != null ? username.toString() : "unknown";
    }

    /**
     * Validate JWT claims locally (expiry, issuer) without signature verification.
     * Returns true if the token appears valid. This is defense-in-depth —
     * the backend performs full validation on every API call.
     */
    boolean validateTokenClaims(String accessToken) {
        Map<String, Object> claims = parseJwtClaims(accessToken);
        if (claims == null) return false;

        // Check expiry
        Object exp = claims.get("exp");
        if (exp instanceof Number) {
            long expiresAt = ((Number) exp).longValue();
            if (System.currentTimeMillis() / 1000 > expiresAt) {
                LOG.debugf("Token expired (exp=%d)", expiresAt);
                return false;
            }
        }

        // Check issuer matches KeyCloak (if configured)
        Object iss = claims.get("iss");
        if (iss instanceof String && !((String) iss).isBlank()) {
            String issuer = (String) iss;
            if (!issuer.contains("osir") && !issuer.contains("keycloak") && !issuer.contains("auth")) {
                LOG.warnf("Unexpected token issuer: %s", issuer);
                return false;
            }
        }

        return true;
    }

    private String parseOAuthError(WebApplicationException e) {
        try {
            String body = e.getResponse().readEntity(String.class);
            TokenErrorResponse errorResponse = MAPPER.readValue(body, TokenErrorResponse.class);
            return errorResponse.getError() != null ? errorResponse.getError() : "unknown_error";
        } catch (Exception parseEx) {
            LOG.warnf("Failed to parse OAuth error response: %s", parseEx.getMessage());
            return "unknown_error";
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String generateSessionId() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "session_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Visible for testing
    Map<String, SessionInfo> getSessions() {
        return sessions;
    }

    String getCurrentSessionId() {
        return currentSessionId;
    }

    void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    Map<String, Long> getPendingDeviceFlows() {
        return pendingDeviceFlows;
    }

    /**
     * Restore a session from externally stored credentials (e.g., CLI credential store).
     * This allows the CLI module to hydrate AuthService with a previously persisted token.
     */
    public void restoreSession(String username, String accessToken, String tokenType,
                               long expiresAt, String refreshToken, String loginMethod) {
        String sessionId = generateSessionId();
        SessionInfo session = new SessionInfo(username, accessToken, tokenType, expiresAt, refreshToken, loginMethod);
        sessions.put(sessionId, session);
        currentSessionId = sessionId;
        LOG.infof("Session restored for user: %s (method: %s)", username, loginMethod);
    }

    static class SessionInfo {
        private final String username;
        private final String accessToken;
        private final String tokenType;
        private final long expiresAt;
        private final String refreshToken;
        private final String loginMethod; // "password" or "device"

        public SessionInfo(String username, String accessToken, String tokenType,
                           long expiresAt, String refreshToken, String loginMethod) {
            this.username = username;
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresAt = expiresAt;
            this.refreshToken = refreshToken;
            this.loginMethod = loginMethod;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public String getUsername() { return username; }
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public long getExpiresAt() { return expiresAt; }
        public String getRefreshToken() { return refreshToken; }
        public String getLoginMethod() { return loginMethod; }
    }
}
