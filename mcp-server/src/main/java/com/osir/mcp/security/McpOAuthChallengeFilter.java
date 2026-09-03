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

    public void register(@Observes Filters filters) {
        final String challenge = "Bearer resource_metadata=\""
                + resourceBaseUrl + "/.well-known/oauth-protected-resource\"";
        filters.register(rc -> {
            if (enabled
                    && isMcpTransport(rc.normalizedPath())
                    && !"OPTIONS".equals(rc.request().method().name())
                    && !hasBearer(rc)) {
                LOG.debugf("Unauthenticated %s %s, issuing OAuth challenge",
                        rc.request().method().name(), rc.normalizedPath());
                rc.response()
                        .setStatusCode(401)
                        .putHeader("WWW-Authenticate", challenge)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"unauthorized\",\"error_description\":\"Authorization required. "
                                + "Authenticate via OAuth; see the WWW-Authenticate header.\"}");
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
