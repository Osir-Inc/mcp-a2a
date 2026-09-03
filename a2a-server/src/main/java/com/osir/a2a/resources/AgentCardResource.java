package com.osir.a2a.resources;

import com.osir.a2a.agents.AgentRegistry;
import com.osir.a2a.protocol.AgentCard;
import com.osir.a2a.protocol.Skill;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serves the A2A Agent Card at /.well-known/agent.json
 * This is the discovery endpoint for A2A-compliant clients.
 */
@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class AgentCardResource {

    @Inject
    AgentRegistry agentRegistry;

    @Context
    UriInfo uriInfo;

    // Public base URL override for deployments behind a proxy that rewrites host.
    // ponytail: falls back to the request's own base URI when unset.
    @ConfigProperty(name = "a2a.public-url")
    Optional<String> publicUrl;

    @ConfigProperty(name = "a2a.documentation-url", defaultValue = "https://github.com/Osir-Inc/mcp-a2a")
    String documentationUrl;

    @GET
    @Path("/agent.json")
    public AgentCard getAgentCard() {
        // Build the top-level agent card that represents this server
        AgentCard card = new AgentCard();
        card.setName("OSIR Agent Platform");
        card.setDescription("AI-powered domain registrar platform with specialist agents for domain management, DNS, VPS, billing, and more.");
        card.setUrl(base() + "a2a");
        card.setVersion("1.0.0");
        card.setDocumentationUrl(documentationUrl);
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        // streaming (/a2a/stream) and push notifications (webhookUrl) are both implemented
        card.setCapabilities(new AgentCard.AgentCapabilities(true, true));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        // How to actually obtain the bearer (audit F7: "bearer" alone gave agents no path in).
        card.setSecuritySchemes(java.util.Map.of(
                "oauth", java.util.Map.of(
                        "type", "openIdConnect",
                        "openIdConnectUrl", "https://auth.osir.com/realms/osir/.well-known/openid-configuration",
                        "description", "OAuth 2.0 via Keycloak. Anonymous dynamic client registration is enabled; "
                                + "authorization_code+PKCE, device_code, and client_credentials grants are supported. "
                                + "Send the access token as 'Authorization: Bearer <token>'.")));

        // Aggregate skills from all registered specialist agents
        List<Skill> allSkills = agentRegistry.getAllAgentCards().stream()
                .filter(c -> c.getSkills() != null)
                .flatMap(c -> c.getSkills().stream())
                .collect(Collectors.toList());
        card.setSkills(allSkills);

        return card;
    }

    @GET
    @Path("/agents")
    public List<AgentCard> listAgents() {
        return agentRegistry.getAllAgentCards();
    }

    /** Absolute base URL (with trailing slash): configured override, else the request's own base. */
    private String base() {
        String b = publicUrl.filter(s -> !s.isBlank()).orElseGet(() -> uriInfo.getBaseUri().toString());
        return b.endsWith("/") ? b : b + "/";
    }
}
