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

    @Tool(description = "getDomainAuditTrail: Get the audit trail (history of all changes) for a specific domain. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get domain audit trail",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AuditTrailResult getDomainAuditTrail(@ToolArg(description = "Fully qualified domain name to fetch the audit trail for (e.g. 'example.com').") String domain,@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return auditService.getDomainAuditTrail(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain audit trail: %s", e.getMessage());
            return new AuditTrailResult(false, "Failed to get domain audit trail: " + e.getMessage());
        }
    }

    @Tool(description = "getMyAuditLogs: Get recent audit logs for the authenticated user across all services. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get my audit logs",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AuditLogListResult getMyAuditLogs(@ToolArg(required = false, description = "Zero-based page number for pagination.") Integer page, @ToolArg(required = false, description = "Number of log entries per page.") Integer size,@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return auditService.getMyAuditLogs(page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error getting audit logs: %s", e.getMessage());
            return new AuditLogListResult(false, "Failed to get audit logs: " + e.getMessage());
        }
    }

    @Tool(description = "getRecentActivity: Get the most recent activity across all domains and services for the user. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get recent activity",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public RecentActivityResult getRecentActivity(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return auditService.getRecentActivity();
        } catch (Exception e) {
            Log.errorf(e, "Error getting recent activity: %s", e.getMessage());
            return new RecentActivityResult(false, "Failed to get recent activity: " + e.getMessage());
        }
    }
}
