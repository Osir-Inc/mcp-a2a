package com.osir.a2a.resources;

import com.osir.a2a.agents.AgentRegistry;
import com.osir.a2a.protocol.AgentCard;
import com.osir.a2a.protocol.Skill;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
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

    @GET
    @Path("/agent.json")
    public AgentCard getAgentCard() {
        // Build the top-level agent card that represents this server
        AgentCard card = new AgentCard();
        card.setName("OSIR Agent Platform");
        card.setDescription("AI-powered domain registrar platform with specialist agents for domain management, DNS, VPS, billing, and more.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));

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
}
