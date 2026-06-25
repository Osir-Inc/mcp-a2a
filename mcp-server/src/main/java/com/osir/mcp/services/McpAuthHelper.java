package com.osir.mcp.services;

import io.quarkiverse.mcp.server.McpConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Bridges the per-connection session token into the request-scoped AuthContext
 * so that all downstream services automatically use the correct user's token.
 */
@ApplicationScoped
public class McpAuthHelper {

    private static final Logger LOG = Logger.getLogger(McpAuthHelper.class);

    @Inject
    SessionAwareAuthService sessionService;

    @Inject
    Instance<AuthContext> authContextInstance;

    public void setupAuth(McpConnection connection) {
        if (connection == null || !authContextInstance.isResolvable()) return;
        String token = sessionService.getCurrentToken(connection.id());
        if (token != null) {
            authContextInstance.get().setTokenOverride(token);
        } else {
            LOG.debugf("No token for connection %s — call will fail auth check in service layer", connection.id());
        }
    }
}
