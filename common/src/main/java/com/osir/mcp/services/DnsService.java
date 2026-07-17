package com.osir.mcp.services;

import com.osir.mcp.clients.DnsBackendClient;
import com.osir.mcp.models.dns.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class DnsService {

    private static final Logger LOG = Logger.getLogger(DnsService.class);

    @Inject
    @RestClient
    DnsBackendClient backendClient;

    @Inject
    AuthService authService;

    public DnsRecordListResult listRecords(String domain) {
        if (!authService.isAuthenticated()) {
            return new DnsRecordListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<DnsRecord> records = backendClient.listDnsRecords(domain, token);
            DnsRecordListResult result = new DnsRecordListResult(true, "DNS records retrieved successfully");
            result.setDomain(domain);
            result.setRecords(records);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing DNS records for %s: %s", domain, e.getMessage());
            return new DnsRecordListResult(false, "Failed to list DNS records: " + e.getMessage());
        }
    }

    public DnsActionResult initializeZone(String domain) {
        if (!authService.isAuthenticated()) {
            return new DnsActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            backendClient.createDnsZone(domain, token);
            LOG.infof("DNS zone initialized for %s", domain);
            return new DnsActionResult(true, "DNS zone initialized for " + domain);
        } catch (Exception e) {
            LOG.warnf("DNS zone init for %s: %s", domain, e.getMessage());
            return new DnsActionResult(false, "Failed to initialize DNS zone: " + e.getMessage());
        }
    }

    public DnsRecordResult createRecord(String domain, String name, String type, String content, Integer ttl, Integer priority) {
        if (!authService.isAuthenticated()) {
            return new DnsRecordResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        String token = authService.getCurrentToken();
        DnsRecordRequest request = new DnsRecordRequest(name, type, content, ttl != null ? ttl : 3600, priority != null ? priority : 0);

        try {
            DnsRecord record = backendClient.createDnsRecord(domain, request, token);
            DnsRecordResult result = new DnsRecordResult(true, "DNS record created successfully");
            result.setRecord(record);
            return result;
        } catch (Exception first) {
            if (!isServerError(first)) {
                LOG.errorf(first, "Error creating DNS record for %s: %s", domain, first.getMessage());
                return new DnsRecordResult(false, "Failed to create DNS record: " + first.getMessage());
            }
            // Zone likely not initialized — create it and retry once
            LOG.warnf("DNS record creation failed for %s (likely missing zone), initializing zone and retrying", domain);
            try {
                backendClient.createDnsZone(domain, token);
            } catch (Exception zoneEx) {
                LOG.warnf("Zone init for %s returned: %s (continuing retry)", domain, zoneEx.getMessage());
            }
            try {
                DnsRecord record = backendClient.createDnsRecord(domain, request, token);
                DnsRecordResult result = new DnsRecordResult(true, "DNS record created successfully");
                result.setRecord(record);
                return result;
            } catch (Exception retry) {
                LOG.errorf(retry, "Error creating DNS record for %s after zone init: %s", domain, retry.getMessage());
                return new DnsRecordResult(false, "Failed to create DNS record: " + retry.getMessage());
            }
        }
    }

    private static boolean isServerError(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse().getStatus() >= 500;
        }
        return e.getMessage() != null && e.getMessage().contains("status code 5");
    }

    public DnsRecordResult updateRecord(String domain, String recordId, String name, String type, String content, Integer ttl, Integer priority) {
        if (!authService.isAuthenticated()) {
            return new DnsRecordResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            DnsRecordRequest request = new DnsRecordRequest(name, type, content, ttl, priority);
            DnsRecord record = backendClient.updateDnsRecord(domain, recordId, request, token);
            DnsRecordResult result = new DnsRecordResult(true, "DNS record updated successfully");
            result.setRecord(record);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error updating DNS record %s for %s: %s", recordId, domain, e.getMessage());
            return new DnsRecordResult(false, "Failed to update DNS record: " + e.getMessage());
        }
    }

    public DnsActionResult deleteRecord(String domain, String recordId) {
        if (!authService.isAuthenticated()) {
            return new DnsActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            backendClient.deleteDnsRecord(domain, recordId, token);
            return new DnsActionResult(true, "DNS record deleted successfully");
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting DNS record %s for %s: %s", recordId, domain, e.getMessage());
            return new DnsActionResult(false, "Failed to delete DNS record: " + e.getMessage());
        }
    }

    public DnsRecordResult getRecord(String domain, String recordId) {
        if (!authService.isAuthenticated()) {
            return new DnsRecordResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            DnsRecord record = backendClient.getDnsRecord(domain, recordId, token);
            DnsRecordResult result = new DnsRecordResult(true, "DNS record retrieved successfully");
            result.setRecord(record);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting DNS record %s for %s: %s", recordId, domain, e.getMessage());
            return new DnsRecordResult(false, "Failed to get DNS record: " + e.getMessage());
        }
    }
}
