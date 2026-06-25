package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.host.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.HostService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class HostMCPServer {

    @Inject
    HostService hostService;

    @Inject
    PendingActionStore pendingActionStore;

    @Tool(description = "Check if a host/glue record name is available for creation. Requires authentication. Required: hostname (e.g., 'ns1.example.com')")
    public HostCheckResult checkHostAvailability(String hostname, McpConnection connection) {
        try {
            return hostService.checkAvailability(hostname);
        } catch (Exception e) {
            Log.errorf(e, "Error checking host availability: %s", e.getMessage());
            return new HostCheckResult(false, "Host availability check failed: " + e.getMessage(), false, hostname);
        }
    }

    @Tool(description = "Create a new host/glue record (e.g., for custom nameservers). Requires authentication. Required: hostname (e.g., 'ns1.example.com'), ipAddresses (e.g., ['192.0.2.1', '198.51.100.1'])")
    public HostResult createHost(String hostname, List<String> ipAddresses, McpConnection connection) {
        try {
            return hostService.createHost(hostname, ipAddresses);
        } catch (Exception e) {
            Log.errorf(e, "Error creating host: %s", e.getMessage());
            return new HostResult(false, "Host creation failed: " + e.getMessage());
        }
    }

    @Tool(description = "List all host/glue records associated with a domain. Requires authentication. Required: domain (e.g., 'example.com')")
    public HostListResult getHostsForDomain(String domain, McpConnection connection) {
        try {
            return hostService.getHostsForDomain(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error listing hosts for domain: %s", e.getMessage());
            return new HostListResult(false, "Failed to list host records: " + e.getMessage());
        }
    }

    @Tool(description = "Stage deletion of a host/glue record. DESTRUCTIVE — irreversible. Requires authentication. Required: hostname (e.g., 'ns1.example.com'). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteHost(String hostname, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteHost",
                "Permanently delete host/glue record '" + hostname + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> hostService.deleteHost(hostname)
        );
    }
}
