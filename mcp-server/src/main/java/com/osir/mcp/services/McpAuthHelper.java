package com.osir.mcp.services;

import com.osir.mcp.models.AuthStatusResult;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Single resolver for the caller's identity and token on an MCP request. Every
 * auth touchpoint (the @RequiresAuth interceptor, getAuthStatus, logout, per-tool
 * setupAuth, and confirmation ownership) routes through here so the
 * "Bearer header wins over session" precedence lives in exactly one place.
 *
 * Three token sources, in priority order:
 * 1. An {@code Authorization: Bearer} header on the MCP HTTP request, sent by
 *    OAuth connectors on every call. Survives MCP session churn: such clients
 *    may open a new session (new connection id) per call.
 * 2. A conversation session key ({@code osk_*}) passed as the {@code sessionKey}
 *    tool argument, minted by checkDeviceLoginStatus and carried in the LLM
 *    conversation, so it also survives session churn without any OAuth setup.
 * 3. The per-connection session established via the device-login tools.
 *
 * Signature validation is the backend's job; the only local check here is a
 * provable-expiry gate (a numeric {@code exp} in the past), so getAuthStatus and
 * the interceptor agree on whether a token is usable.
 */
@ApplicationScoped
public class McpAuthHelper {

    private static final Logger LOG = Logger.getLogger(McpAuthHelper.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    SessionAwareAuthService sessionService;

    @Inject
    AuthService authService;

    @Inject
    Instance<AuthContext> authContextInstance;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    /**
     * Resolve the caller's token and publish it to the request-scoped AuthContext so
     * downstream services use it. Returns true if a usable token was established.
     */
    public boolean setupAuth(McpConnection connection) {
        return setupAuth(connection, null);
    }

    public boolean setupAuth(McpConnection connection, String sessionKey) {
        if (!authContextInstance.isResolvable()) return false;
        String token = resolveToken(connection, sessionKey);
        if (token != null) {
            authContextInstance.get().setTokenOverride(token);
            return true;
        }
        if (connection != null) {
            LOG.debugf("No usable token for connection %s, call will fail auth check in service layer", connection.id());
        }
        return false;
    }

    /** The token to authenticate with: Bearer header > conversation session key > connection session. */
    private String resolveToken(McpConnection connection, String sessionKey) {
        String bearer = bearerToken();
        if (bearer != null && !isLocallyExpired(bearer.substring(BEARER_PREFIX.length()))) {
            return bearer;
        }
        if (sessionKey != null && sessionKey.startsWith(SessionAwareAuthService.SESSION_KEY_PREFIX)) {
            String token = sessionService.getCurrentToken(sessionKey);
            if (token != null) return token;
        }
        return connection == null ? null : sessionService.getCurrentToken(connection.id());
    }

    /** The raw "Bearer xxx" Authorization header of the current HTTP request, or null. */
    public String bearerToken() {
        try {
            RoutingContext rc = currentVertxRequest.getCurrent();
            if (rc == null) return null;
            String header = rc.request().getHeader("Authorization");
            if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                    && header.length() > BEARER_PREFIX.length()) {
                return header;
            }
        } catch (Exception e) {
            LOG.debugf("Could not read Authorization header: %s", e.getMessage());
        }
        return null;
    }

    /**
     * A stable identity for the authenticated caller, used to key confirmation ownership
     * and rate limiting so they survive MCP session churn. Prefers the token's {@code sub}
     * claim (stable per user across sessions/tokens); falls back to the connection id only
     * when no JWT subject is available.
     */
    public String currentPrincipal(McpConnection connection) {
        return currentPrincipal(connection != null ? connection.id() : null);
    }

    public String currentPrincipal(String fallbackConnectionId) {
        if (authContextInstance.isResolvable() && authContextInstance.get().hasOverride()) {
            Map<String, Object> claims = authService.parseJwtClaims(strip(authContextInstance.get().getTokenOverride()));
            if (claims != null) {
                Object sub = claims.get("sub");
                if (sub != null && !sub.toString().isBlank()) return "user:" + sub;
            }
        }
        return fallbackConnectionId != null ? "conn:" + fallbackConnectionId : "conn:unknown";
    }

    /**
     * Auth status derived from a request Bearer token, or null when the request has none.
     * Reports authenticated unless the token is a JWT with a numeric {@code exp} in the past,
     * so opaque/reference tokens (accepted by every tool) don't read as logged-out.
     */
    public AuthStatusResult bearerAuthStatus() {
        String bearer = bearerToken();
        if (bearer == null) return null;
        Map<String, Object> claims = authService.parseJwtClaims(bearer.substring(BEARER_PREFIX.length()));
        if (claims == null) {
            return new AuthStatusResult(true, "unknown", null);
        }
        Long expiresIn = null;
        if (claims.get("exp") instanceof Number exp) {
            expiresIn = exp.longValue() - (System.currentTimeMillis() / 1000);
            if (expiresIn <= 0) return new AuthStatusResult(false, null, null);
        }
        Object username = claims.get("preferred_username");
        return new AuthStatusResult(true, username != null ? username.toString() : "unknown", expiresIn);
    }

    private boolean isLocallyExpired(String rawToken) {
        Map<String, Object> claims = authService.parseJwtClaims(rawToken);
        if (claims == null) return false; // opaque token, can't prove expiry, let the backend decide
        return claims.get("exp") instanceof Number exp && System.currentTimeMillis() / 1000 > exp.longValue();
    }

    private static String strip(String token) {
        if (token == null) return null;
        return token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                ? token.substring(BEARER_PREFIX.length()) : token;
    }
}
