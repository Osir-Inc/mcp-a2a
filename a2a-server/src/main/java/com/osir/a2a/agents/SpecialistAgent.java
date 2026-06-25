package com.osir.a2a.agents;

import com.osir.a2a.protocol.AgentCard;
import com.osir.a2a.protocol.A2ATask;

/**
 * Interface for all A2A specialist agents.
 * Each agent handles a specific domain of operations.
 *
 * Auth context is set per-request via {@link com.osir.mcp.services.AuthContext}
 * before the agent is invoked — agents do not need to handle tokens directly.
 */
public interface SpecialistAgent {

    /** Unique identifier for this agent, used for routing. */
    String getId();

    /** The agent card describing this agent's capabilities. */
    AgentCard getAgentCard();

    /**
     * Score how well this agent can handle the given task (0.0 = cannot handle, 1.0 = perfect match).
     * Used by AgentRegistry for routing when multiple agents could handle a task.
     */
    double score(A2ATask task);

    /** Execute the task. The agent should update the task state and add artifacts. */
    A2ATask handle(A2ATask task);
}
