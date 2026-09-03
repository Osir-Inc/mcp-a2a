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
import io.quarkiverse.mcp.server.ToolArg;
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

    @Tool(description = "Check if a host/glue record name is available for creation. Requires authentication. Returns whether the hostname is free; call before createHost.",
            annotations = @Tool.Annotations(
                    title = "Check host availability",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public HostCheckResult checkHostAvailability(
            @ToolArg(description = "Fully qualified host name to check, e.g. 'ns1.example.com'.") String hostname,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return hostService.checkAvailability(hostname);
        } catch (Exception e) {
            Log.errorf(e, "Error checking host availability: %s", e.getMessage());
            return new HostCheckResult(false, "Host availability check failed: " + e.getMessage(), false, hostname);
        }
    }

    @Tool(description = "Create a new host/glue record, e.g. for custom nameservers like 'ns1.example.com'. Requires authentication. Check the name first with checkHostAvailability. Returns the created host record.",
            annotations = @Tool.Annotations(
                    title = "Create host record",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public HostResult createHost(
            @ToolArg(description = "Fully qualified host name to create, e.g. 'ns1.example.com'.") String hostname,
            @ToolArg(description = "IP addresses for the host, IPv4 dotted-quad or IPv6, e.g. ['192.0.2.1', '198.51.100.1'].") List<String> ipAddresses,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return hostService.createHost(hostname, ipAddresses);
        } catch (Exception e) {
            Log.errorf(e, "Error creating host: %s", e.getMessage());
            return new HostResult(false, "Host creation failed: " + e.getMessage());
        }
    }

    @Tool(description = "List all host/glue records associated with a domain. Requires authentication. Returns each host name and its IP addresses.",
            annotations = @Tool.Annotations(
                    title = "List host records",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public HostListResult getHostsForDomain(
            @ToolArg(description = "Fully qualified domain name whose host records to list, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return hostService.getHostsForDomain(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error listing hosts for domain: %s", e.getMessage());
            return new HostListResult(false, "Failed to list host records: " + e.getMessage());
        }
    }

    @Tool(description = "Stage deletion of a host/glue record. DESTRUCTIVE and irreversible once executed. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Delete host record",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult deleteHost(
            @ToolArg(description = "Fully qualified host name to delete, e.g. 'ns1.example.com'.") String hostname,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteHost",
                "Permanently delete host/glue record '" + hostname + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> hostService.deleteHost(hostname)
        );
    }
}
