package com.osir.mcp.telemetry;

import com.osir.mcp.clients.UserAgentClientFilter;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * POST /v1/agent/telemetry (backend B.6), batch ingest, ≤200 events per call.
 * API keys go in X-API-Key, NOT Authorization: Bearer, Bearer is routed to OIDC and 401s
 * (handoff §5).
 */
@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TelemetryClient {

    @POST
    @Path("/v1/agent/telemetry")
    void ingest(
            @HeaderParam("X-API-Key") String apiKey,
            List<Map<String, Object>> events
    );
}
