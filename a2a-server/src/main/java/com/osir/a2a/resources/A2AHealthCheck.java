package com.osir.a2a.resources;

import com.osir.a2a.agents.AgentRegistry;
import com.osir.a2a.protocol.TaskStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class A2AHealthCheck implements HealthCheck {

    @Inject AgentRegistry agentRegistry;
    @Inject TaskStore taskStore;

    @Override
    public HealthCheckResponse call() {
        int agentCount = agentRegistry.getAllAgents().size();
        return HealthCheckResponse.named("a2a-server")
                .status(agentCount > 0)
                .withData("agents", agentCount)
                .withData("active-tasks", taskStore.size())
                .build();
    }
}
