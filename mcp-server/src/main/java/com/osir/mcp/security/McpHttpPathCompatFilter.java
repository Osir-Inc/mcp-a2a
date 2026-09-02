package com.osir.mcp.security;

import io.quarkus.vertx.http.runtime.filters.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Backwards compatibility for the published Streamable HTTP URL.
 *
 * quarkus-mcp-server 1.x served Streamable HTTP at /mcp/http (our published connector URL —
 * README, osir.com, every connected client). 2.x serves it at the root-path (/mcp) and the
 * legacy SSE transport at /mcp/sse. This filter reroutes /mcp/http to /mcp so both the old
 * and the new URL work. Do not remove without a client deprecation period.
 */
@ApplicationScoped
public class McpHttpPathCompatFilter {

    public void register(@Observes Filters filters) {
        // Higher priority than McpOAuthChallengeFilter (100) so the reroute happens first;
        // rerouting re-runs the router, so the challenge filter still sees the rewritten path.
        filters.register(rc -> {
            String path = rc.normalizedPath();
            if (path.equals("/mcp/http") || path.equals("/mcp/http/")) {
                rc.reroute("/mcp");
                return;
            }
            rc.next();
        }, 200);
    }
}
