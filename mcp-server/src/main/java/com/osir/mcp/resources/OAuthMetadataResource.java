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
 * RFC 8414 — OAuth 2.0 Authorization Server Metadata.
 *
 * Served at the same origin as the MCP server (be.osir.com) as required by
 * the MCP authorization spec. All OAuth operations delegate to KeyCloak;
 * this document just tells the connector where to find them.
 *
 * The issuer matches the iss claim in KeyCloak-issued tokens so that
 * connectors can validate tokens correctly.
 */
@Path("/.well-known")
@ApplicationScoped
public class OAuthMetadataResource {

    @ConfigProperty(name = "keycloak.auth-server-url", defaultValue = "https://auth.osir.com")
    String keycloakUrl;

    @ConfigProperty(name = "keycloak.realm", defaultValue = "osir")
    String realm;

    @GET
    @Path("/oauth-authorization-server")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> metadata() {
        String oidcBase = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect";
        // issuer must match the iss claim in tokens; KeyCloak sets iss = realm URL
        String issuer = keycloakUrl + "/realms/" + realm;
        // KeyCloak Dynamic Client Registration endpoint (RFC 7591 / OIDC DCR)
        String registrationEndpoint = keycloakUrl + "/realms/" + realm + "/clients-registrations/openid-connect";

        return Map.of(
                "issuer", issuer,
                "authorization_endpoint", oidcBase + "/auth",
                "token_endpoint", oidcBase + "/token",
                "registration_endpoint", registrationEndpoint,
                "token_endpoint_auth_methods_supported", List.of("none"),
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of(
                        "authorization_code",
                        "refresh_token",
                        "urn:ietf:params:oauth:grant-type:device_code"
                ),
                "code_challenge_methods_supported", List.of("S256"),
                "scopes_supported", List.of("openid", "profile", "email")
        );
    }
}
