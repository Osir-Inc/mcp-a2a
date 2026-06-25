package com.osir.a2a.agents;

import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.AgentCard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Registry of all specialist agents available in this server.
 * Agents are discovered via CDI injection and routed by score.
 */
@ApplicationScoped
public class AgentRegistry {

    private static final Logger LOG = Logger.getLogger(AgentRegistry.class);

    @Inject
    Instance<SpecialistAgent> agents;

    public List<SpecialistAgent> getAllAgents() {
        return StreamSupport.stream(agents.spliterator(), false)
                .collect(Collectors.toList());
    }

    public List<AgentCard> getAllAgentCards() {
        return getAllAgents().stream()
                .map(SpecialistAgent::getAgentCard)
                .collect(Collectors.toList());
    }

    public Optional<SpecialistAgent> findById(String agentId) {
        return getAllAgents().stream()
                .filter(a -> a.getId().equals(agentId))
                .findFirst();
    }

    /**
     * Find the best agent to handle a task by scoring all agents and picking the highest.
     * Agents with score <= 0 are excluded. Each agent is scored exactly once.
     */
    public Optional<SpecialistAgent> findAgentForTask(A2ATask task) {
        SpecialistAgent best = null;
        double bestScore = 0.0;

        for (SpecialistAgent agent : agents) {
            double s = agent.score(task);
            if (s > bestScore) {
                bestScore = s;
                best = agent;
            }
        }

        if (best != null) {
            LOG.debugf("Task %s routed to agent: %s (score=%.2f)", task.getId(), best.getId(), bestScore);
        }
        return Optional.ofNullable(best);
    }
}
