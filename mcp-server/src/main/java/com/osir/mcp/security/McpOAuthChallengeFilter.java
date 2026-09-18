package com.osir.mcp.security;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * MCP authorization challenge (RFC 9728 / MCP authorization spec).
 *
 * The MCP transport endpoints are OAuth-protected. An unauthenticated request gets a 401
 * whose WWW-Authenticate header points at the protected-resource metadata, that challenge
 * is what makes an OAuth-capable client (Claude.ai) run discovery + browser login and then
 * send {@code Authorization: Bearer} on every call. A Bearer header is the only identity that
 * survives such a client opening a new MCP session (new connection id) per request.
 *
 * Without the challenge the client stays anonymous and falls back to the in-band device-login
 * tools, whose token has no stable per-connection home. The CLI authenticates directly against
 * Keycloak and does not use these endpoints, so gating them on OAuth costs it nothing.
 *
 * Two connector URLs, one deployment (prod runs challenge-enabled=false):
 *   /mcp/http  - Claude.ai "No sign-in": anonymous tools + in-chat device login (sessionKey).
 *   /mcp/oauth - Claude.ai "Sign in now": always challenged; OAuth client id mcp-client.
 */
@ApplicationScoped
public class McpOAuthChallengeFilter {

    private static final Logger LOG = Logger.getLogger(McpOAuthChallengeFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    // Public origin that serves the protected-resource metadata (same host as the MCP endpoint).
    @ConfigProperty(name = "mcp.authorization-server.url", defaultValue = "https://be.osir.com")
    String resourceBaseUrl;

    // Escape hatch: set false to serve the MCP endpoints without the OAuth gate (e.g. local dev).
    @ConfigProperty(name = "mcp.oauth.challenge-enabled", defaultValue = "true")
    boolean enabled;

    /** Always-OAuth alias of the Streamable HTTP endpoint, gated whatever {@link #enabled} says. */
    public static final String OAUTH_PATH = "/mcp/oauth";

    public void register(@Observes Filters filters) {
        final String challenge = "Bearer resource_metadata=\""
                + resourceBaseUrl + "/.well-known/oauth-protected-resource\"";
        final String oauthChallenge = "Bearer resource_metadata=\""
                + resourceBaseUrl + "/.well-known/oauth-protected-resource" + OAUTH_PATH + "\"";
        filters.register(rc -> {
            String path = rc.normalizedPath();
            boolean oauthPath = path.equals(OAUTH_PATH) || path.equals(OAUTH_PATH + "/");
            if ((oauthPath || (enabled && isMcpTransport(path)))
                    && !"OPTIONS".equals(rc.request().method().name())
                    && !hasBearer(rc)) {
                LOG.debugf("Unauthenticated %s %s, issuing OAuth challenge",
                        rc.request().method().name(), path);
                rc.response()
                        .setStatusCode(401)
                        .putHeader("WWW-Authenticate", oauthPath ? oauthChallenge : challenge)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"unauthorized\",\"error_description\":\"Authorization required. "
                                + "Authenticate via OAuth; see the WWW-Authenticate header.\"}");
                return;
            }
            // Past the gate, the OAuth URL is plain Streamable HTTP.
            if (oauthPath) {
                rc.reroute("/mcp");
                return;
            }
            rc.next();
        }, 100);
    }

    private static boolean isMcpTransport(String path) {
        return path.equals("/mcp") || path.startsWith("/mcp/");
    }

    private static boolean hasBearer(RoutingContext rc) {
        String h = rc.request().getHeader("Authorization");
        return h != null
                && h.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && h.length() > BEARER_PREFIX.length();
    }
}
