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

    @Tool(description = "initializeDnsZone: Initialize (create) the DNS zone for a domain. NOT needed after registerDomain, which initializes the zone automatically. Use only for pre-existing domains without a zone (e.g. after a transfer, or if registration opted out with initializeDnsZone:false). Safe on existing zones, it will not overwrite records. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Initialize DNS zone",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DnsActionResult initializeDnsZone(@ToolArg(description = "Fully qualified domain name to create the zone for, e.g. 'example.com'.") String domain, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return dnsService.initializeZone(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error initializing DNS zone: %s", e.getMessage());
            return new DnsActionResult(false, "Failed to initialize DNS zone: " + e.getMessage());
        }
    }

    @Tool(description = "listDnsRecords: List all DNS records for a domain. Requires authentication. Returns each record with its id, name, type, content, TTL, and priority; use the record id with getDnsRecord, updateDnsRecord, or deleteDnsRecord.",
            annotations = @Tool.Annotations(
                    title = "List DNS records",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DnsRecordListResult listDnsRecords(@ToolArg(description = "Fully qualified domain name whose records to list, e.g. 'example.com'.") String domain, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return dnsService.listRecords(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error listing DNS records: %s", e.getMessage());
            return new DnsRecordListResult(false, "Failed to list DNS records: " + e.getMessage());
        }
    }

    @Tool(description = "createDnsRecord: Create a new DNS record for a domain. Requires authentication. For newly registered domains the zone is initialized automatically if missing. Returns the created record including its id for later updates or deletion.",
            annotations = @Tool.Annotations(
                    title = "Create DNS record",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DnsRecordResult createDnsRecord(
            @ToolArg(description = "Fully qualified domain name the record belongs to, e.g. 'example.com'.") String domain,
            @ToolArg(description = "Record name relative to the zone, e.g. 'www', 'mail', or '@' for the apex.") String name,
            @ToolArg(description = "Record type: A, AAAA, CNAME, MX, TXT, NS, SRV, CAA.") String type,
            @ToolArg(description = "Record value, e.g. an IPv4 dotted-quad or IPv6 address for A/AAAA, a hostname for CNAME/MX/NS, or text for TXT.") String content,
            @ToolArg(required = false, description = "Time to live in seconds; defaults to 3600 when omitted.") Integer ttl,
            @ToolArg(required = false, description = "Priority for MX/SRV records only; defaults to 0 when omitted.") Integer priority,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return dnsService.createRecord(domain, name, type, content, ttl, priority);
        } catch (Exception e) {
            Log.errorf(e, "Error creating DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to create DNS record: " + e.getMessage());
        }
    }

    @Tool(description = "updateDnsRecord: Update an existing DNS record. Requires authentication. Only the fields you provide are changed; omitted fields keep their current values. Get the recordId from listDnsRecords. Returns the updated record.",
            annotations = @Tool.Annotations(
                    title = "Update DNS record",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DnsRecordResult updateDnsRecord(
            @ToolArg(description = "Fully qualified domain name the record belongs to, e.g. 'example.com'.") String domain,
            @ToolArg(description = "Identifier of the record to update, as returned by listDnsRecords.") String recordId,
            @ToolArg(required = false, description = "New record name relative to the zone, e.g. 'www' or '@' for the apex.") String name,
            @ToolArg(required = false, description = "New record type: A, AAAA, CNAME, MX, TXT, NS, SRV, CAA.") String type,
            @ToolArg(required = false, description = "New record value, e.g. an IPv4 dotted-quad or IPv6 address, hostname, or text.") String content,
            @ToolArg(required = false, description = "New time to live in seconds.") Integer ttl,
            @ToolArg(required = false, description = "New priority for MX/SRV records only.") Integer priority,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return dnsService.updateRecord(domain, recordId, name, type, content, ttl, priority);
        } catch (Exception e) {
            Log.errorf(e, "Error updating DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to update DNS record: " + e.getMessage());
        }
    }

    @Tool(description = "deleteDnsRecord: Stage deletion of a DNS record. DESTRUCTIVE and irreversible once executed. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Delete DNS record",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult deleteDnsRecord(
            @ToolArg(description = "Fully qualified domain name the record belongs to, e.g. 'example.com'.") String domain,
            @ToolArg(description = "Identifier of the record to delete, as returned by listDnsRecords.") String recordId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteDnsRecord",
                "Permanently delete DNS record '" + recordId + "' from domain '" + domain + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> dnsService.deleteRecord(domain, recordId)
        );
    }

    @Tool(description = "getDnsRecord: Get details of a specific DNS record by id. Requires authentication. Get the recordId from listDnsRecords. Returns the record's name, type, content, TTL, and priority.",
            annotations = @Tool.Annotations(
                    title = "Get DNS record",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DnsRecordResult getDnsRecord(
            @ToolArg(description = "Fully qualified domain name the record belongs to, e.g. 'example.com'.") String domain,
            @ToolArg(description = "Identifier of the record to fetch, as returned by listDnsRecords.") String recordId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return dnsService.getRecord(domain, recordId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting DNS record: %s", e.getMessage());
            return new DnsRecordResult(false, "Failed to get DNS record: " + e.getMessage());
        }
    }
}
