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

import java.security.SecureRandom;
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

    /** Conversation session keys go stale after this much inactivity — bounds standing access. */
    @ConfigProperty(name = "mcp.session.idle-timeout-minutes", defaultValue = "30")
    long idleTimeoutMinutes;

    /** Absolute ceiling on a conversation session's lifetime, regardless of activity. */
    @ConfigProperty(name = "mcp.session.max-lifetime-hours", defaultValue = "8")
    long maxLifetimeHours;

    /** Prefix distinguishing conversation session keys from MCP connection ids in the store. */
    public static final String SESSION_KEY_PREFIX = "osk_";

    /** Scopes requested at device login — shared with the common AuthService. */
    private static final String DEVICE_SCOPES = AuthService.DEVICE_SCOPES;

    private static final SecureRandom RANDOM = new SecureRandom();

    // connection ID or conversation session key (osk_*) -> SessionAuth
    private final Map<String, SessionAuth> sessionAuths = new ConcurrentHashMap<>();

    // PKCE code_verifier per pending device code (Keycloak enforces S256 on public clients,
    // including the device grant). Keyed by DEVICE CODE, not connection — Claude.ai opens a new
    // MCP session between loginWithDevice and the poll, so connection-scoped state would be lost.
    // If the MCP server restarts mid-login the verifier is gone and the user restarts the login.
    private record PendingPkce(String verifier, long expiresAtMillis) {}
    private final Map<String, PendingPkce> devicePkce = new ConcurrentHashMap<>();

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
            // The same SessionAuth is stored under both the connection id and the osk_ session
            // key; purge every alias so a logged-out token can't authenticate via the twin entry.
            sessionAuths.entrySet().removeIf(e -> e.getValue() == removed);
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

    /**
     * Log out a caller authenticated purely via a request Bearer token (no device session).
     * Best-effort revokes the access token at Keycloak; a stateless JWT the client still holds
     * stays signature-valid until expiry, so the message says so rather than over-promising.
     */
    public AuthResult revokeBearer(String bearerToken) {
        String raw = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        try {
            keycloakClient.revokeToken(raw, "access_token", clientId);
            LOG.infof("Revoked bearer access token at Keycloak");
        } catch (Exception e) {
            LOG.warnf("Failed to revoke bearer token at Keycloak: %s", e.getMessage());
        }
        return new AuthResult(true,
                "Signed out on the server and revoked the token at Keycloak. If your client caches the "
                        + "access token it stays valid until it expires — remove it there to fully sign out.");
    }

    public String getCurrentToken(String connectionId) {
        SessionAuth sessionAuth = sessionAuths.get(connectionId);

        if (sessionAuth == null) {
            return null;
        }

        // Conversation session keys carry idle + absolute lifetime limits so access is
        // bounded even though the refresh token could technically renew indefinitely.
        if (connectionId.startsWith(SESSION_KEY_PREFIX) && sessionAuth.isStale(
                idleTimeoutMinutes * 60_000L, maxLifetimeHours * 3_600_000L)) {
            LOG.infof("Conversation session for user %s expired (idle/lifetime limit), revoking", sessionAuth.getUsername());
            sessionAuths.entrySet().removeIf(e -> e.getValue() == sessionAuth); // include the conn-id twin
            revokeQuietly(sessionAuth);
            return null;
        }
        sessionAuth.touch();

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
            // Lazy purge of abandoned logins (device codes expire in ~10 min).
            long now = System.currentTimeMillis();
            devicePkce.entrySet().removeIf(e -> e.getValue().expiresAtMillis() < now);

            String codeVerifier = com.osir.mcp.util.Pkce.newVerifier();
            DeviceCodeResponse response = keycloakClient.requestDeviceCode(clientId, DEVICE_SCOPES,
                    com.osir.mcp.util.Pkce.challengeS256(codeVerifier), com.osir.mcp.util.Pkce.METHOD_S256);
            if (response == null) {
                return new DeviceLoginResult(false, "Failed to initiate device login: no response from KeyCloak");
            }
            devicePkce.put(response.getDeviceCode(),
                    new PendingPkce(codeVerifier, now + response.getExpiresIn() * 1000L));
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

    // KeyCloak stays the source of truth for device codes (RFC 8628); the only local state is
    // the PKCE verifier, keyed by device code (see devicePkce) so it survives Claude.ai opening
    // a new MCP session between loginWithDevice and the poll.
    public DeviceLoginStatusResult checkDeviceLoginStatus(String connectionId, String deviceCode) {
        try {
            PendingPkce pkce = devicePkce.get(deviceCode);
            if (pkce == null) {
                // Server restarted (or code never issued here): the verifier is unrecoverable.
                return new DeviceLoginStatusResult(false,
                        "This login attempt is no longer valid (server restarted). Please start a new login.",
                        "invalid");
            }
            AuthTokenResponse tokenResponse = keycloakClient.pollDeviceToken(
                    "urn:ietf:params:oauth:grant-type:device_code", clientId, deviceCode, pkce.verifier());

            devicePkce.remove(deviceCode);
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

            // Conversation session key: the stable handle for clients (Claude.ai) that open a
            // new MCP session per tool call — the key lives in the conversation, not the session.
            String sessionKey = newSessionKey();
            sessionAuths.put(sessionKey, sessionAuth);

            // Keycloak's SSO-session idle window (refresh_expires_in) caps how long the refresh
            // token works regardless of our clock — advertise whichever limit bites first so the
            // login message never over-promises.
            long effectiveIdleMinutes = idleTimeoutMinutes;
            if (tokenResponse.getRefreshExpiresIn() != null && tokenResponse.getRefreshExpiresIn() > 0) {
                effectiveIdleMinutes = Math.min(idleTimeoutMinutes, tokenResponse.getRefreshExpiresIn() / 60);
            }

            LOG.infof("Device login complete for user %s on connection %s", username, connectionId);
            DeviceLoginStatusResult result = new DeviceLoginStatusResult(
                    true,
                    "Authentication successful. Your sessionKey is " + sessionKey
                            + " — pass it as the sessionKey argument on every subsequent tool call. "
                            + "The session ends after " + effectiveIdleMinutes + " minutes of inactivity ("
                            + maxLifetimeHours + "h maximum); call logout with the sessionKey to end it immediately.",
                    "complete",
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getTokenType()
            );
            result.setSessionKey(sessionKey);
            return result;
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
                    devicePkce.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false,
                            "Device code has expired. Please start a new login.", "expired");
                }
                case "access_denied" -> {
                    devicePkce.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false,
                            "Authorization was denied by the user.", "denied");
                }
                case "invalid_grant" -> {
                    devicePkce.remove(deviceCode);
                    yield new DeviceLoginStatusResult(false,
                            "Unknown or already-used device code. Please start a new login.", "invalid");
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
                // Keep the original creation/activity clocks: a refresh must not extend the
                // conversation session's absolute lifetime.
                SessionAuth refreshed = current.refreshedWith(
                        tokenResponse.getAccessToken(),
                        tokenResponse.getTokenType() != null ? tokenResponse.getTokenType() : "Bearer",
                        System.currentTimeMillis() + (tokenResponse.getExpiresIn() != null
                                ? tokenResponse.getExpiresIn() * 1000L : 3600000L),
                        tokenResponse.getRefreshToken() != null
                                ? tokenResponse.getRefreshToken() : current.getRefreshToken()
                );
                // Update EVERY alias (osk_ key + connection id share one SessionAuth). Keycloak
                // rotates refresh tokens (maxReuse=2): a twin left holding the rotated-out token
                // would die on its next refresh. Deliberately no put/putIfAbsent — if a concurrent
                // logout just removed the entries, re-inserting a revoked session would be wrong.
                sessionAuths.replaceAll((k, v) -> v == current ? refreshed : v);
                LOG.infof("Token refreshed for user %s on connection %s", current.getUsername(), connectionId);
                return refreshed.getTokenType() + " " + refreshed.getAccessToken();
            }
        } catch (Exception e) {
            LOG.warnf("Token refresh failed for connection %s: %s", connectionId, e.getMessage());
        }
        sessionAuths.remove(connectionId);
        return null;
    }

    @Scheduled(every = "15m")
    public void cleanupExpiredSessions() {
        int sessionsBefore = sessionAuths.size();
        long idleMs = idleTimeoutMinutes * 60_000L;
        long maxMs = maxLifetimeHours * 3_600_000L;

        // Two passes. Stale osk_ sessions are revoked and removed together with every alias
        // (the same SessionAuth is also stored under a connection id). Expired connection-keyed
        // entries are removed individually — their osk_ alias may have refreshed and still be
        // live, so no alias cascade there.
        Map<SessionAuth, Boolean> staleConversations = new java.util.IdentityHashMap<>();
        java.util.List<String> expiredConnKeys = new java.util.ArrayList<>();
        sessionAuths.forEach((key, auth) -> {
            if (key.startsWith(SESSION_KEY_PREFIX)) {
                if (auth.isStale(idleMs, maxMs)) staleConversations.put(auth, true);
            } else if (auth.isExpired()) {
                expiredConnKeys.add(key);
            }
        });
        // ponytail: serial blocking revocations on the scheduler thread — batch/async if the
        // stale set ever grows into the hundreds per sweep.
        staleConversations.keySet().forEach(this::revokeQuietly);
        sessionAuths.entrySet().removeIf(entry -> staleConversations.containsKey(entry.getValue()));
        expiredConnKeys.forEach(sessionAuths::remove);
        LOG.debugf("Cleanup: removed %d expired sessions", sessionsBefore - sessionAuths.size());
    }

    private String newSessionKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return SESSION_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Best-effort Keycloak revocation when a conversation session ends without an explicit logout. */
    private void revokeQuietly(SessionAuth auth) {
        try {
            if (auth.getRefreshToken() != null) {
                keycloakClient.backchannelLogout(auth.getRefreshToken(), clientId);
            } else {
                keycloakClient.revokeToken(auth.getAccessToken(), "access_token", clientId);
            }
        } catch (Exception e) {
            LOG.debugf("Best-effort revocation failed: %s", e.getMessage());
        }
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

    private static class SessionAuth {
        private final String username;
        private final String accessToken;
        private final String tokenType;
        private final long expiresAt;
        private final String refreshToken;
        private final long createdAt;
        private volatile long lastUsedAt;

        SessionAuth(String username, String accessToken, String tokenType,
                    long expiresAt, String refreshToken) {
            this(username, accessToken, tokenType, expiresAt, refreshToken, System.currentTimeMillis());
        }

        private SessionAuth(String username, String accessToken, String tokenType,
                            long expiresAt, String refreshToken, long createdAt) {
            this.username = username;
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.expiresAt = expiresAt;
            this.refreshToken = refreshToken;
            this.createdAt = createdAt;
            this.lastUsedAt = createdAt;
        }

        /** A refreshed copy that keeps the original session's creation time and activity clock. */
        SessionAuth refreshedWith(String accessToken, String tokenType, long expiresAt, String refreshToken) {
            SessionAuth copy = new SessionAuth(username, accessToken, tokenType, expiresAt, refreshToken, createdAt);
            copy.lastUsedAt = this.lastUsedAt;
            return copy;
        }

        void touch() { this.lastUsedAt = System.currentTimeMillis(); }

        boolean isStale(long idleMs, long maxLifetimeMs) {
            long now = System.currentTimeMillis();
            return now - lastUsedAt > idleMs || now - createdAt > maxLifetimeMs;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }

        String getUsername() { return username; }
        String getAccessToken() { return accessToken; }
        String getTokenType() { return tokenType; }
        long getExpiresAt() { return expiresAt; }
        String getRefreshToken() { return refreshToken; }
    }
}
