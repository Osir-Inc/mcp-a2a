package com.osir.mcp.services;

import com.osir.mcp.clients.AuditBackendClient;
import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.account.UserProfile;
import com.osir.mcp.models.audit.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    @Inject
    @RestClient
    AuditBackendClient auditBackendClient;

    @Inject
    @RestClient
    DomainBackendClient domainBackendClient;

    @Inject
    AuthService authService;

    public AuditTrailResult getDomainAuditTrail(String domain) {
        if (!authService.isAuthenticated()) {
            return new AuditTrailResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            AuditLogListResponse response = auditBackendClient.getDomainAuditTrail(domain, token);
            AuditTrailResult result = new AuditTrailResult(true, "Audit trail retrieved successfully");
            result.setDomain(domain);
            result.setEntries(response.getEntries());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting audit trail for domain %s: %s", domain, e.getMessage());
            return new AuditTrailResult(false, "Failed to get domain audit trail: " + e.getMessage());
        }
    }

    public AuditLogListResult getMyAuditLogs(Integer page, Integer size) {
        if (!authService.isAuthenticated()) {
            return new AuditLogListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            // First get the customer ID from profile
            UserProfile profile = domainBackendClient.getMyProfile(token);
            AuditLogListResponse response = auditBackendClient.getCustomerAuditLogs(profile.getCustomerId(), page, size, token);
            AuditLogListResult result = new AuditLogListResult(true, "Audit logs retrieved successfully");
            result.setEntries(response.getEntries());
            result.setTotalCount(response.getTotalCount());
            result.setPage(response.getPage());
            result.setSize(response.getSize());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting audit logs: %s", e.getMessage());
            return new AuditLogListResult(false, "Failed to get audit logs: " + e.getMessage());
        }
    }

    public RecentActivityResult getRecentActivity() {
        if (!authService.isAuthenticated()) {
            return new RecentActivityResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<AuditEntry> activities = auditBackendClient.getRecentActivity(token);
            RecentActivityResult result = new RecentActivityResult(true, "Recent activity retrieved successfully");
            result.setActivities(activities);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting recent activity: %s", e.getMessage());
            return new RecentActivityResult(false, "Failed to get recent activity: " + e.getMessage());
        }
    }
}
