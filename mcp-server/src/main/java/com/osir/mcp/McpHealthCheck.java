package com.osir.mcp;

import com.osir.mcp.services.DomainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class McpHealthCheck implements HealthCheck {

    @Inject
    DomainService domainService;

    @Override
    public HealthCheckResponse call() {
        try {
            // Test if backend is reachable (optional)
            boolean backendHealthy = testBackendConnection();

            return HealthCheckResponse.named("mcp-server")
                    .status(backendHealthy)
                    .withData("version", "1.0.0")
                    .withData("protocol", "MCP 2025-03-26")
                    .withData("transport", "Streamable HTTP")
                    .withData("endpoint", "/mcp")
                    .build();

        } catch (Exception e) {
            return HealthCheckResponse.named("mcp-server")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }

    private boolean testBackendConnection() {
        try {
            // TODO Needs to do a Health Checkup on the backend service
            // For now, just return true
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}