package com.osir.mcp;

import com.osir.mcp.models.audit.AuditLogListResult;
import com.osir.mcp.models.audit.AuditTrailResult;
import com.osir.mcp.models.audit.RecentActivityResult;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.AuditService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class AuditMCPServer {

    @Inject
    AuditService auditService;

    @Tool(description = "Get the audit trail (history of all changes) for a specific domain. Requires authentication. Required: domain (string)")
    public AuditTrailResult getDomainAuditTrail(String domain, McpConnection connection) {
        try {
            return auditService.getDomainAuditTrail(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain audit trail: %s", e.getMessage());
            return new AuditTrailResult(false, "Failed to get domain audit trail: " + e.getMessage());
        }
    }

    @Tool(description = "Get recent audit logs for the authenticated user across all services. Requires authentication. Optional: page (Integer), size (Integer)")
    public AuditLogListResult getMyAuditLogs(@ToolArg(required = false) Integer page, @ToolArg(required = false) Integer size, McpConnection connection) {
        try {
            return auditService.getMyAuditLogs(page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error getting audit logs: %s", e.getMessage());
            return new AuditLogListResult(false, "Failed to get audit logs: " + e.getMessage());
        }
    }

    @Tool(description = "Get the most recent activity across all domains and services for the user. Requires authentication.")
    public RecentActivityResult getRecentActivity(McpConnection connection) {
        try {
            return auditService.getRecentActivity();
        } catch (Exception e) {
            Log.errorf(e, "Error getting recent activity: %s", e.getMessage());
            return new RecentActivityResult(false, "Failed to get recent activity: " + e.getMessage());
        }
    }
}
