package com.osir.mcp.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * RFC 9728 — OAuth 2.0 Protected Resource Metadata.
 *
 * Claude's MCP connector fetches this first to discover which Authorization
 * Server issues tokens for this resource. The WWW-Authenticate header in the
 * 401 response points here via resource_metadata=...
 *
 * Discovery chain:
 *   401 WWW-Authenticate: Bearer resource_metadata=".../.well-known/oauth-protected-resource"
 *   → GET /.well-known/oauth-protected-resource  (this endpoint)
 *   → authorization_servers: ["https://be.osir.com"]
 *   → GET https://be.osir.com/.well-known/oauth-authorization-server
 *   → authorization_endpoint, token_endpoint, registration_endpoint
 *   → DCR + PKCE browser login
 *   → Bearer token on every subsequent /mcp/http request
 */
@Path("/.well-known")
@ApplicationScoped
public class OAuthProtectedResourceResource {

    @ConfigProperty(name = "mcp.server.url", defaultValue = "https://be.osir.com/mcp/http")
    String mcpServerUrl;

    @ConfigProperty(name = "mcp.authorization-server.url", defaultValue = "https://be.osir.com")
    String authorizationServerUrl;

    @GET
    @Path("/oauth-protected-resource")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> protectedResource() {
        return Map.of(
                "resource", mcpServerUrl,
                "authorization_servers", List.of(authorizationServerUrl),
                "bearer_methods_supported", List.of("header"),
                "scopes_supported", List.of("openid", "profile", "email")
        );
    }
}
