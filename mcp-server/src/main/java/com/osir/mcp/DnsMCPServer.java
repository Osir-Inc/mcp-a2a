package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.dns.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.DnsService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class DnsMCPServer {

    @Inject
    DnsService dnsService;

    @Inject
    PendingActionStore pendingActionStore;

    @Tool(description = "Initialize (create) the DNS zone for a domain. Must be called once after domain registration before any DNS records can be added. Safe to call on existing zones — it will not overwrite records. Required: domain (e.g., 'example.com')")
    public DnsActionResult initializeDnsZone(String domain, McpConnection connection) {
        try {
            return dnsService.initializeZone(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error initializing DNS zone: %s", e.getMessage());
            return new DnsActionResult(false, "Failed to initialize DNS zone: " + e.getMessage());
        }
    }

    @Tool(description = "List all DNS records for a domain. Requires authentication. Required: domain (e.g., 'example.com')")
    public DnsRecordListResult listDnsRecords(String domain, McpConnection connection) {
        try {
            return dnsService.listRecords(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error listing DNS records: %s", e.getMessage());
            return new DnsRecordListResult(false, "Failed to list DNS records: " + e.getMessage());
        }
    }

    @Tool(description = "Create a new DNS record for a domain. Requires authentication. For newly registered domains, the zone is initialized automatically if missing. Required: domain (e.g., 'example.com'), name (e.g., 'www', '@', 'mail'), type ('A', 'AAAA', 'CNAME', 'MX', 'TXT', 'NS', 'SRV'), content (record value). Optional: ttl (seconds, default 3600), priority (for MX/SRV, default 0)")
    public DnsRecordResult createDnsRecord(String domain, String name, String type, String content, @ToolArg(required = false) Integer ttl, @ToolArg(required = false) Integer priority, McpConnection connection) {
        try {
            return dnsService.createRecord(domain, name, type, content, ttl, priority);
        } catch (Exception e) {
            Log.errorf(e, "Error creating DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to create DNS record: " + e.getMessage());
        }
    }

    @Tool(description = "Update an existing DNS record. Requires authentication. Required: domain (e.g., 'example.com'), recordId (string). Optional: name, type, content, ttl, priority")
    public DnsRecordResult updateDnsRecord(String domain, String recordId, @ToolArg(required = false) String name, @ToolArg(required = false) String type, @ToolArg(required = false) String content, @ToolArg(required = false) Integer ttl, @ToolArg(required = false) Integer priority, McpConnection connection) {
        try {
            return dnsService.updateRecord(domain, recordId, name, type, content, ttl, priority);
        } catch (Exception e) {
            Log.errorf(e, "Error updating DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to update DNS record: " + e.getMessage());
        }
    }

    @Tool(description = "Stage deletion of a DNS record. DESTRUCTIVE — irreversible. Requires authentication. Required: domain (e.g., 'example.com'), recordId (string). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteDnsRecord(String domain, String recordId, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteDnsRecord",
                "Permanently delete DNS record '" + recordId + "' from domain '" + domain + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> dnsService.deleteRecord(domain, recordId)
        );
    }

    @Tool(description = "Get details of a specific DNS record. Requires authentication. Required: domain (e.g., 'example.com'), recordId (string)")
    public DnsRecordResult getDnsRecord(String domain, String recordId, McpConnection connection) {
        try {
            return dnsService.getRecord(domain, recordId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to get DNS record: " + e.getMessage());
        }
    }
}
